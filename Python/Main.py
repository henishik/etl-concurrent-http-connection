"""
An application that returns a daily ranking of % performance from 6,000 stocks in NESE and NASDAQ
by connecting YQL public API

Class: Stock
 __init__(self, stock_id, symbol)
 compute_and_set_day_return(self)

Class: ConnectYQLHandler
 __init__(self, target_stock_list)
 create_working_threads(self)
 create_url(self, group_number)
 single_http_connection_get(self, group_number)
 parse_response_json(self, response_json_data)
 compute_and_sort_result_list(self)
 write_result_report_as_text_file(self)
 calculate_total_number_of_connection(self)
 main(self)
"""

import threading
import json
import urllib
import urllib2

import StockList


class Stock(object):
    """
    Stock Model Object.

    A model of an investment stock in financial market
    """
    def __init__(self, stock_id, symbol):
        self.mId = stock_id
        self.symbol = symbol
        self.openPrice = 0
        self.currentPrice = 0
        self.dayReturn = 0

    def compute_and_set_day_return(self):
        """
        Compute daily % return
        """
        try:
            self.dayReturn = (float(self.currentPrice) - float(self.openPrice)) / float(self.openPrice)
        except TypeError:
            self.dayReturn = 0
        except ZeroDivisionError:
            self.dayReturn = 0
        except ValueError:
            self.dayReturn = 0


class ConnectYQLHandler:
    """
    ConnectYQLHandler Class

    <p>A class which connects Yahoo public financial api and makes a report of
    daily ranking of % performance from givenan array of stock symbols.
    """
    NUMBER_PER_ACCESS = 5
    BASE_URL = "https://www.worldtradingdata.com/api/v1/stock"

    stock_array = []
    threads = []
    storingProgressCounter = 0

    def __init__(self, target_stock_list):
        """
        Class Initializer

        :param target_stock_list: An array of String of stock symbol
        :return:
        """
        stock_counter = 0
        for stock in target_stock_list:
            self.stock_array.append(
                Stock(stock_counter, stock))
            stock_counter += 1

    def create_working_threads(self):
        """
        Create all HTTP connection working threads and resume them
        """
        print "Storing price information..."
        number_of_connection = self.calculate_total_number_of_connection()

        for idx in range(number_of_connection):
            t = threading.Thread(
                target=self.single_http_connection_get,
                args=(idx,))
            self.threads.append(t)
            t.start()

        for thread in self.threads:
            thread.join()

    def create_url(self, group_number):
        """
        Create a url for a connection with YQL to get stock information to
        calculate daily % return

        :param group_number: This is an integer to determine stocks to be
        included in URL
        :return: encoded URL
        """

        url_symbol_names = ""
        start_array_index = self.NUMBER_PER_ACCESS * group_number
        end_array_index = start_array_index + self.NUMBER_PER_ACCESS
        itrCounter = 0

        for idx in range(start_array_index, end_array_index):
            if itrCounter == 0:
                url_symbol_names += "" + self.stock_array[idx].symbol + ""
            else:
                url_symbol_names += "," + self.stock_array[idx].symbol + ""
            itrCounter += 1

        query_args = {
            'symbol': url_symbol_names,
            'api_token': '***'
        }

        search_url = "{0}?{1}".format(self.BASE_URL, urllib.urlencode(query_args))

        return search_url

    def single_http_connection_get(self, group_number):
        """
        create a HTTP connection

        :param group_number:
        """
        url = self.create_url(group_number)

        try:
            response = urllib2.urlopen(url).read()
            json_response = json.loads(response)

            self.parse_response_json(json_response)
        except urllib2.URLError as e:
            print "Error when querying the Yahoo Finance API: ", e

    def parse_response_json(self, response_json_data):
        """
        Parse string JSON and store open and current price for a Stock object

        :param response_json_data:
        """
        if response_json_data['data']:
            result_list = response_json_data['data']

            for result in result_list:
                for x in self.stock_array:
                    if x.symbol == result['symbol']:
                        x.openPrice = result['close_yesterday']
                        x.currentPrice = result['price_open']
                        break
        else:
            print "NO RESPONSE"

        self.storingProgressCounter += 1
        print "Complete Group: {0}/{1}".format(
            self.storingProgressCounter,
            self.calculate_total_number_of_connection())

    def compute_and_sort_result_list(self):
        """
        Compute daily return and sort them order by higher % return
        """
        print "Computing Daily Performance..."
        for stock in self.stock_array:
            stock.compute_and_set_day_return()

    def write_result_report_as_text_file(self):
        """
        Create a new text file and write the final report
        """
        print "Sorting..."
        sorted_stock_array = sorted(
            self.stock_array, key=lambda x: x.dayReturn, reverse=True)

        output_string = ""
        for stock in sorted_stock_array:
            output_string += "Symbol: {0}, Return: {1}\n".format(
                stock.symbol,  stock.dayReturn)

        with open("ranking_stock_returns_python.txt", "a") as report_file:
            report_file.write(output_string)

        print "ALL Done!"

    def calculate_total_number_of_connection(self):
        """
        Calculate and return a total number of connection
        """
        return len(self.stock_array) / self.NUMBER_PER_ACCESS

    def main(self):
        """
        1. create all working threads and resume them
        2. compute % return and sort them by higher %
        3. create a report file with the final result
        """

        # 1. create all working threads and resume them
        self.create_working_threads()

        print "Done Storing! All stocks: {0}".format(len(self.stock_array))

        # 2. compute % return and sort them by higher %
        self.compute_and_sort_result_list()

        # 3. create a report file with the final result
        self.write_result_report_as_text_file()


if __name__ == "__main__":
    ConnectYQLHandler(StockList.stock_list_small).main()
