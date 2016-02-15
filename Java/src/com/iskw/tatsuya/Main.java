/*
 * An application that returns a daily ranking of % performance from 6,000 stocks in NESE and NASDAQ
 * by connecting YQL public API
 *
 * Class: Constants
 *  + Integer NUMBER_PER_ACCESS
 *  + String BASE_URL
 *
 * Class: Stock
 *  + Stock(Integer mId, String symbol)
 *  + computeAndSetDayReturn()
 *  + getSymbol()
 *  + getDayReturn()
 *  + setOpenPrice(Double openPrice)
 *  + setCurrentPrice(Double currentPrice)
 *
 * Class: ConnectYQLHandler
 *  + ConnectYQLHandler(ArrayList<String> targetStockList)
 *  - createWorkThreads()
 *  - createURL(int groupNo)
 *  - singleHTTPConnectionGet(int groupNo)
 *  - parseResponseJson(String responseJsonString)
 *  - computeAndSortResultList()
 *  - writeResultReportAsTxt()
 *  - calculateTotalNumberOfConnection()
 *  + main()
 */

package com.iskw.tatsuya;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

class Constants {
    public static final Integer NUMBER_PER_ACCESS = 200;
    public static final String BASE_URL = "http://query.yahooapis.com/v1/public/yql";
}

/**
 * Stock Model Object.
 *
 * <p>A model of an investment stock in financial market
 */
class Stock {
    private Integer mId;
    private String symbol;
    private Double openPrice;
    private Double currentPrice;
    private Double dayReturn;

    public Stock(Integer mId, String symbol) {
        this.mId = mId;
        this.symbol = symbol;
        this.openPrice = 0d;
        this.currentPrice = 0d;
        this.dayReturn = 0d;
    }

    /** Compute daily % return */
    public void computeAndSetDayReturn() {
        this.dayReturn = (this.currentPrice - this.openPrice) / this.openPrice;
    }

    /** Getters */
    public String getSymbol() {
        return symbol;
    }
    public Double getDayReturn() {
        return dayReturn;
    }

    /** Setters */
    public void setOpenPrice(Double openPrice) {
        this.openPrice = openPrice;
    }
    public void setCurrentPrice(Double currentPrice) {
        this.currentPrice = currentPrice;
    }
}

/**
 * ConnectYQLHandler Class
 *
 * <p>A class which connects Yahoo public financial api and makes a report of daily ranking of % performance from given
 * an array of stock symbols.
 */
class ConnectYQLHandler {
    private final String USER_AGENT = "Mozilla/5.0";
    private ArrayList<Stock> stockArray = new ArrayList<>();
    private ArrayList<Thread> threads = new ArrayList<>();
    private int storingProgressCounter = 0;

    /**
     * Constructor
     * @param targetStockList An array of String of stock symbol
     */
    public ConnectYQLHandler(ArrayList<String> targetStockList) {
        Integer stockCounter = 0;
        for (String stock: targetStockList) {
            this.stockArray.add(new Stock(stockCounter, stock));
            stockCounter++;
        }
    }

    /** Create all HTTP connection working threads and resume them */
    private void createWorkThreads() {
        System.out.println("Storing price information...");
        int number_of_connection = this.calculateTotalNumberOfConnection();

        try {
            for (int i = 0; i < number_of_connection; i++) {
                final int groupNumber = i;

                Thread thread = new Thread(new Runnable() {
                    private int mGroupNumber = groupNumber;
                    @Override
                    public void run() {
                        try {
                            singleHTTPConnectionGet(this.mGroupNumber);
                        } catch (Exception e) {
                            System.out.print("Exception during HTTP Connection: " + e);
                        }
                    }
                });

                this.threads.add(thread);
                thread.start();
            }
        } catch (Exception e) {
            System.out.print("Exception during HTTP Connection: " + e);
        }

        for (Thread t: this.threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                System.out.print("Exception during HTTP Connection: " + e);
            }
        }
    }

    /**
     * Create a url for a connection with YQL to get stock information to calculate daily % return
     *
     * @param groupNo This is an integer to determine stocks to be included in URL
     * @return encoded URL
     */
    private String createURL(int groupNo) {
        String url_symbol_names = "";
        int start_array_index = Constants.NUMBER_PER_ACCESS * groupNo;
        int end_array_index = start_array_index + Constants.NUMBER_PER_ACCESS - 1;
        int itrCounter = 0;

        for (int i = start_array_index; i <= end_array_index; i++) {
            if (itrCounter == 0) {
                url_symbol_names += "\"" + this.stockArray.get(i).getSymbol() + "\"";
            } else {
                url_symbol_names += ",\"" + this.stockArray.get(i).getSymbol() + "\"";
            }

            itrCounter++;
        }

        String url_arg_q = "select * from yahoo.finance.quotes where symbol in (" + url_symbol_names + ")";
        String url_arg_format = "json";
        String url_arg_diagnostics = "true";
        String url_arg_env = "store://datatables.org/alltableswithkeys&callback=";

        String search_url = "";
        try {
            search_url = Constants.BASE_URL
                    + "?q=" + URLEncoder.encode(url_arg_q, "UTF-8").replace("+", "%20")
                    + "&format=" + url_arg_format
                    + "&diagnostics=" + url_arg_diagnostics
                    + "&env=" + url_arg_env;
        } catch (UnsupportedEncodingException e) {
            System.out.print("UnsupportedEncodingException: " + e);
        }

        return search_url;
    }

    /**
     * create a HTTP connection
     *
     * @param groupNo identify of thread group
     * @throws Exception
     */
    private void singleHTTPConnectionGet(int groupNo) throws Exception {
        String url = this.createURL(groupNo);
        URL obj = new URL(url);

        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", USER_AGENT);

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        this.parseResponseJson(response.toString());
    }

    /**
     * Parse string JSON and store open and current price for a Stock object
     *
     * @param responseJsonString json string from Yahoo api
     * @throws JSONException
     */
    private void parseResponseJson(String responseJsonString) throws JSONException {
        JSONObject jsonObj = new JSONObject(responseJsonString);
        JSONArray arr = jsonObj.getJSONObject("query").getJSONObject("results").getJSONArray("quote");

        for (int i = 0; i < arr.length(); i++) {
            JSONObject quote = arr.getJSONObject(i);

            for (Stock s: this.stockArray) {
                if (s.getSymbol().equals(quote.getString("symbol"))) {
                    if (quote.isNull("Open")) {
                        s.setOpenPrice(0d);
                    } else {
                        try {
                            s.setOpenPrice(quote.getDouble("Open"));
                        } catch (JSONException e) {
                            System.out.println("+++ CONVERTING EXCEPTION! = SYMBOL: " + quote.getString("symbol"));
                            s.setOpenPrice(0d);
                            continue;
                        }
                    }

                    if (quote.isNull("LastTradePriceOnly")) {
                        s.setCurrentPrice(0d);
                    } else {
                        try {
                            s.setCurrentPrice(quote.getDouble("LastTradePriceOnly"));
                        } catch (JSONException e) {
                            System.out.println("+++ CONVERTING EXCEPTION! = SYMBOL: " + quote.getString("symbol"));
                            s.setCurrentPrice(0d);
                        }
                    }
                }
            }
        }
        this.storingProgressCounter++;
        System.out.println("Complete Group: " + this.storingProgressCounter + "/" + this.calculateTotalNumberOfConnection());
    }

    /** Compute daily return and sort them order by higher % return */
    private void computeAndSortResultList() {
        System.out.println("Computing Daily Performance...");
        for (Stock s: this.stockArray) {
            s.computeAndSetDayReturn();
        }

        System.out.println("Sorting...");
        Collections.sort(this.stockArray, new Comparator<Stock>() {
            @Override
            public int compare(Stock c1, Stock c2) {
                return -Double.compare(c1.getDayReturn(), c2.getDayReturn());
            }
        });
    }

    /** Create a new text file and write the final report */
    private void writeResultReportAsTxt() {
        String output_string = "";
        for (Stock s: this.stockArray) {
            s.computeAndSetDayReturn();
            output_string += "Symbol: " + s.getSymbol() + ", Return: " + s.getDayReturn() + "\n";
        }

        Writer writer = null;

        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("ranking_stock_returns_java.txt"), "utf-8"));
            writer.write(output_string);
        } catch (IOException e) {
            System.out.print("IOException: " + e);
        } finally {
            try {
                writer.close();
            } catch (Exception e) {
                System.out.print("Exception: " + e);
            }
        }
        System.out.print("ALL Done!");
    }

    /** Calculate and return a total number of connection */
    private int calculateTotalNumberOfConnection() {
        return this.stockArray.size() / Constants.NUMBER_PER_ACCESS;
    }


    /**
     * Class main function
     *  1. create all working threads and resume them
     *  2. compute % return and sort them by higher %
     *  3. create a report file with the final result
     */
    public void main() {
        // 1. create all working threads and resume them
        this.createWorkThreads();

        System.out.println("Done Storing! All stocks: " + this.stockArray.size());

        // 2. compute % return and sort them by higher %
        this.computeAndSortResultList();

        // 3. create a report file with the final result
        this.writeResultReportAsTxt();
    }
}

public class Main {
    public static void main(String[] args) {
        new ConnectYQLHandler(new StockList().stockList).main();
    }
}
