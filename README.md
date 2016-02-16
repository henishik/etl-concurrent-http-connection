# ConcurrentHTTPConnection

a simple http client application that returns a daily ranking of % performance from 5,000 stocks by connectiong Yahoo public API

# What this does

1.	Load 5,000 target stocks as array
2.	Divide into 25 groups (200 stocks per group)
3.	Connect to Yahoo public API to get daily stock price information concurrently
4.	Parse response json data for furthre culcuration
5.	Compute daily percent return
6.	Create a text file and write a report ranking of daily returns order by higher returns
