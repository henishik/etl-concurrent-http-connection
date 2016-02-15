/*
 * An application that returns a daily ranking of % performance from 6,000 stocks in NESE and NASDAQ
 * by connecting YQL public API
 *
 * Class: Constants
 *  NUMBER_PER_ACCESS
 *  BASE_URL
 *
 * Class: Stock
 *  init (symbol: String, id: Int)
 *  setOpenAndCurrentPrice(openPrice: Float, currentPrice: Float)
 *  computeAndSetDayReturn()
 *
 * Class: ConnectYQLHandler
 *  init (targetStocksList: Array<String>)
 *  createWorkThreads()
 *  createURL(int groupNo)
 *  parseResponseJson(String responseJsonString)
 *  computeAndSortResultList()
 *  writeResultReportAsTxt()
 *  calculateTotalNumberOfConnection()
 *  numberOfObjectsInStockArray()
 *  main_loop()
 */

import Foundation

struct Constants {
    static let NUMBER_PER_ACCESS = 200
    static let BASE_URL = "http://query.yahooapis.com/v1/public/yql"
}

/**
 * Stock Model Object.
 *
 * <p>A model of an investment stock in financial market
 */
class Stock {
    var id: Int
    var symbol: String
    var openPrice: Float
    var currentPrice: Float
    var dayReturn: Float

    init (symbol: String, id: Int) {
        self.id = id
        self.symbol = symbol
        self.openPrice = 0
        self.currentPrice = 0
        self.dayReturn = 0
    }

    /** Setter */
    func setOpenAndCurrentPrice(openPrice: Float, currentPrice: Float) {
        self.openPrice = openPrice
        self.currentPrice = currentPrice
    }

    /** Compute daily % return */
    func computeAndSetDayReturn() {
        self.dayReturn = (self.currentPrice - self.openPrice) / self.openPrice
    }
}

/**
 * ConnectYQLHandler Class
 *
 * <p>A class which connects Yahoo public financial api and makes a report of daily ranking of % performance from given
 * an array of stock symbols.
 */
class ConnectYQLHandler {
    var isDoneTask: Bool
    var isConnecting: Bool

    var storingProgressCounter: Int = 0
    var group: dispatch_group_t
    var allResponses: [AnyObject] = []
    var stockArray: Array<Stock> = []

    /**
    * Class Initializer
    * - parameter targetStockList: An array of String of stock symbol
    */
    init (targetStocksList: Array<String>) {
        self.isDoneTask = false
        self.isConnecting = false
        self.group = dispatch_group_create()

        var idCount = 0
        for stock in targetStocksList {
            idCount++
            self.stockArray.append(Stock(symbol: stock, id: idCount))
        }
    }

    /** Create all HTTP connection working threads and resume them */
    func createWorkThreads() {
        let number_of_connection: Int = self.calculateTotalNumberOfConnection() - 1

        print ("Storing price information...")
        for i in 0...number_of_connection {
            let url = NSURL(string: self.createURL(i))

            dispatch_group_enter(self.group)
            let downloadTask = NSURLSession.sharedSession().downloadTaskWithURL(url!, completionHandler: {
                (location, response, error) in

                if (error == nil) {
                    let objectData = NSData(contentsOfURL: location!)
                    let tmpData: NSString = NSString(data: objectData!, encoding: NSUTF8StringEncoding)!
                    let data: NSData = tmpData.dataUsingEncoding(NSUTF8StringEncoding)!

                    self.parseResponseJson(data)
                } else {
                    print("Failed")
                }

                dispatch_group_leave(self.group)
            })

            downloadTask.resume()
        }
    }

    /**
    * Create a url for a connection with YQL to get stock information to calculate daily % return
    *
    * - parameter groupNo: This is an integer to determine stocks to be included in URL
    * - return encoded URL
    */
    func createURL(group_number: Int) -> String {
        var url_symbol_names = ""
        var loopCount = 0
        let start_array_index = Constants.NUMBER_PER_ACCESS * group_number
        let end_array_index = start_array_index + Constants.NUMBER_PER_ACCESS - 1

        for i in start_array_index...end_array_index {
            if loopCount == 0 {
                url_symbol_names = "\"\(self.stockArray[i].symbol)\""
            } else {
                url_symbol_names += ",\"\(self.stockArray[i].symbol)\""
            }

            loopCount++
        }

        let url_arg_q = "select symbol, Open, LastTradePriceOnly from yahoo.finance.quotes where symbol in (\(url_symbol_names))"
        let url_arg_format = "json"
        let url_arg_diagnostics = "true"
        let url_arg_env = "store://datatables.org/alltableswithkeys&callback="

        let search_url = Constants.BASE_URL + "?q=" + url_arg_q + "&format=" + url_arg_format + "&diagnostics=" + url_arg_diagnostics + "&env=" + url_arg_env
        // !Later iOS 7
        let encodedString = search_url.stringByAddingPercentEncodingWithAllowedCharacters(NSCharacterSet.URLQueryAllowedCharacterSet())

        return encodedString!
    }

    /**
    * Parse string JSON and store open and current price for a Stock object
    *
    * - parameter responseJsonString json string from Yahoo api
    * - throws JSONException
    */
    func parseResponseJson(responseData: NSData) {
        do {
            let anyObj = try NSJSONSerialization.JSONObjectWithData(responseData, options: [])
            if let dict: NSDictionary = anyObj as? NSDictionary {
                let query_dict = dict["query"]
                let result_dict = query_dict!["results"]
                let quote_dict = result_dict!!["quote"]
                let resultList: NSArray = quote_dict as! NSArray

                for result in resultList {
                    let resultDict = result as? NSDictionary
                    self.allResponses.append(resultDict!["Open"]!)

                    if let i = self.stockArray.indexOf({$0.symbol == resultDict!["symbol"] as! String}) {
                        let targetStock: Stock = self.stockArray[i] as Stock

                        var openPrice: Float = 0
                        if let openPriceTmp = resultDict!["Open"] {
                            if !(openPriceTmp is NSNull) {
                                openPrice = (openPriceTmp).floatValue
                            }
                        }

                        var currentPrice: Float = 0
                        if let curretPriceTmp = resultDict!["LastTradePriceOnly"] {
                            if !(curretPriceTmp is NSNull) {
                                currentPrice = (curretPriceTmp).floatValue
                            }
                        }

                        targetStock.setOpenAndCurrentPrice(openPrice, currentPrice: currentPrice)
                    }
                }

                print("Complete Group: \(self.storingProgressCounter + 1)/\(self.calculateTotalNumberOfConnection())")

                self.storingProgressCounter++
            } else {
                print("not a dictionary")
            }
        } catch let error as NSError {
            print("json error: \(error.localizedDescription)")
        }

    }


    /** Compute daily return and sort them order by higher % return */
    func computeAndSortResultList() {
        print("Done Storing! All stocks: \(self.stockArray.count)")
        print("Computing Daily Performance...")
        for stock: Stock in self.stockArray {
            stock.computeAndSetDayReturn()
        }
    }

    /** Create a new text file and write the final report */
    func writeResultReportAsTxt() {
        print("Sorting...")
        let sortedArray = self.stockArray.sort({ $0.dayReturn > $1.dayReturn })

        var outputString = ""
        for stock: Stock in sortedArray {
            outputString += "Symbol: \(stock.symbol), Return: \(stock.dayReturn)\n"
        }

        let file = "ranking_stock_returns_swift.txt"
        if let dir : NSString = NSSearchPathForDirectoriesInDomains(NSSearchPathDirectory.DocumentDirectory, NSSearchPathDomainMask.AllDomainsMask, true).first {
            let path = dir.stringByAppendingPathComponent(file);
            do {
                try outputString.writeToFile(path, atomically: false, encoding: NSUTF8StringEncoding)
            }
            catch {/* error handling here */}
        }

        print("ALL Done!")
    }

    /** Calculate and return a total number of connection */
    func calculateTotalNumberOfConnection() -> Int {
        return self.numberOfObjectsInStockArray() / Constants.NUMBER_PER_ACCESS;
    }

    /** Return number of Stock array as Int */
    func numberOfObjectsInStockArray() -> Int {
        return self.stockArray.count
    }

    /**
    * Class main function: create a main loop function by myself ohterwise
    * execution will stop while another threads are working unlike java or python.
    *
    *  1. create all working threads and resume them
    *  2. compute % return and sort them by higher %
    *  3. create a report file with the final result
    */
    func main_loop() {
        // Finish excecution when all task is done
        while !self.isDoneTask {
            // let script run only one time
            if !self.isConnecting {
                self.isConnecting = true

                // 1. create all working threads and resume them
                self.createWorkThreads()

                dispatch_group_notify(self.group, dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0)) { () -> Void in
                    // 2. compute % return and sort them by higher %
                    self.computeAndSortResultList()

                    // 3. create a report file with the final result
                    self.writeResultReportAsTxt()

                    self.isDoneTask = true
                }
            }
        }
    }
}

let httpClient = ConnectYQLHandler(targetStocksList: Stocks().targetStockList)
httpClient.main_loop()

