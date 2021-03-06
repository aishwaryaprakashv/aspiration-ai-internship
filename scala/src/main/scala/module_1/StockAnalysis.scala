package module_1

import java.time.LocalDate

import pprint.pprintln

import scala.Numeric._
import scala.collection.immutable.ListMap
//import scala.util.chainingOps._  // Requires scala v2.13 - incompatible with kantan

object StockAnalysis extends App {
  val input_csv_filename:   String  = if (args.length >= 1) args(0) else "../data/stocks/Mid_Cap/MUTHOOTFIN.csv"
  val output_csv_filename:  String  = if (args.length >= 2) args(1) else "../data_output/module_1/week2-scala.csv"
  val output_json_filename: String  = if (args.length >= 3) args(2) else "../data_output/module_1/week2-scala.json"

  var stockPrices: List[StockPrice] = StockPriceCSV.read(input_csv_filename)

  stockPrices = filter_not_eq(stockPrices)
  stockPrices = setYesterdayStockPrices(stockPrices)

  StockAnalysis.print(stockPrices)
  StockPriceCSV.write_csv(output_csv_filename,    stockPrices)
  StockPriceCSV.write_json(output_json_filename, stats(stockPrices))

  def print( stockPrices: List[StockPrice] ) {
    println(this.getClass.getSimpleName)
    println(input_csv_filename)
    println("\nstockPrices.take(5)");  pprintln(stockPrices.take(5))

    pprintln( stats(stockPrices) )
  }

  def stats( stockPrices: List[StockPrice] ): ListMap[String, ListMap[String, Double]]  = {
    ListMap(
      "stats_90_day_close_price"     -> stats_90_day_close_price(stockPrices),
      "stats_vwap_by_month"          -> stats_vwap_by_month(stockPrices),
      "stats_average_price"          -> stats_average_price(stockPrices),
      "stats_profit_loss_percentage" -> stats_profit_loss_percentage(stockPrices),
      "stats_quantity_trend"         -> stats_quantity_trend(stockPrices)
    )
  }


  def filter_not_eq( stockPrices: List[StockPrice]): List[StockPrice] = {
    stockPrices.filter( _.Series == "EQ" )
  }

  def filter_days( stockPrices: List[StockPrice], days: Int ): List[StockPrice] = {
    // DOCS: https://stackoverflow.com/questions/38059191/how-make-implicit-ordered-on-java-time-localdate
    implicit val localDateOrdering: Ordering[LocalDate] = _ compareTo _

    stockPrices.length match {
      case 0 => stockPrices
      case _ =>
        val date_end:    LocalDate = stockPrices.map(_.Date).max
        val date_cutoff: LocalDate = date_end.minusDays(days)

        stockPrices.filter(_.Date.compareTo(date_cutoff) > 0)
    }
  }

  def setYesterdayStockPrices( stockPrices: List[StockPrice] ): List[StockPrice] = {
    for( (stockPrice, index) <- stockPrices.view.zipWithIndex ) {
      val yesterdayStockPrice = index match {
        case 0 => stockPrice
        case _ => stockPrices(index-1)
      }
      stockPrice.setYesterday(yesterdayStockPrice)
    }
    stockPrices
  }

  def vwap( stockPrices: List[StockPrice] ): Double = {
    val total = stockPrices.map(stockPrice => stockPrice.Close_Price * stockPrice.Total_Traded_Quantity).sum
    val price = stockPrices.map(stockPrice => stockPrice.Total_Traded_Quantity).sum
    total / price
  }

  def stats_vwap_by_month( stockPrices: List[StockPrice] ): ListMap[String,Double] = {
    val vwap_by_month = stockPrices
      .groupBy(stockPrice => f"${stockPrice.Date.getYear}-${stockPrice.Date.getMonthValue}%02d")
      .mapValues(group => vwap(group))

    ListMap( vwap_by_month.toSeq.sortBy(_._1):_* )  // months need to be zero padded to sort correctly
  }

  // 1.5 Write a function to calculate the average price over the last N days of the stock price data where N is a user defined parameter.
  def average_price(stockPrices: List[StockPrice], days: Int): Double = {
    val prices = filter_days(stockPrices, days).map(_.Close_Price)

    prices.length match {
      case 0 => 0
      case 1 => prices.head
      case _ => grizzled.math.stats.mean( prices.head, prices.tail:_* )  // Weird syntax: https://github.com/bmc/grizzled-scala/blob/master/src/test/scala/grizzled/math/StatsSpec.scala
    }
  }

  // 1.5 Write a second function to calculate the profit/loss percentage over the last N days
  def profit_loss_percentage(stockPrices: List[StockPrice], days: Int): Double = {
    val prices = filter_days(stockPrices, days).map(_.Close_Price)

    prices.length match {
      case 0 => 0
      case 1 => 0
      case _ => (prices.last - prices.head) / prices.head * 100
    }
  }

  // 1.2 Calculate the maximum, minimum and mean price for the last 90 days. (price=Closing Price unless stated otherwise)
  def stats_90_day_close_price(stockPrices: List[StockPrice]): ListMap[String, Double] = {
    val prices = filter_days(this.stockPrices, 90).map(_.Close_Price)

    prices.length match {
      case 0 => ListMap(
        "min"  -> 0,
        "max"  -> 0,
        "mean" -> 0
      )
      case _ => ListMap(
        "min"  -> prices.min,
        "max"  -> prices.max,
        "mean" -> grizzled.math.stats.mean(prices.head, prices.tail: _*)
      )
    }
  }

  /**
    * 1.5 Calculate the average price AND the profit/loss percentages over the course of
    * last - 1 week, 2 weeks, 1 month, 3 months, 6 months and 1 year.
    */
  def stats_average_price( stockPrices: List[StockPrice] ): ListMap[String, Double] = {
    ListMap(
      "1 week"   -> average_price(stockPrices, 7 * 1),
      "2 weeks"  -> average_price(stockPrices, 7 * 2),
      "1 month"  -> average_price(stockPrices, 365 / 12 * 1),
      "2 months" -> average_price(stockPrices, 365 / 12 * 2),
      "6 months" -> average_price(stockPrices, 365 / 12 * 6),
      "1 year"   -> average_price(stockPrices, 365),
    )
  }

  /**
    * 1.5 Calculate the average price AND the profit/loss percentages over the course of
    * last - 1 week, 2 weeks, 1 month, 3 months, 6 months and 1 year.
    */
  def stats_profit_loss_percentage(stockPrices: List[StockPrice] ): ListMap[String, Double] = {
    ListMap(
      "1 week"   -> profit_loss_percentage(stockPrices, 7 * 1),
      "2 weeks"  -> profit_loss_percentage(stockPrices, 7 * 2),
      "1 month"  -> profit_loss_percentage(stockPrices, 365 / 12 * 1),
      "2 months" -> profit_loss_percentage(stockPrices, 365 / 12 * 2),
      "6 months" -> profit_loss_percentage(stockPrices, 365 / 12 * 6),
      "1 year"   -> profit_loss_percentage(stockPrices, 365),
    )
  }

  // 1.8: Find the average and median values of the column 'Total Traded Quantity' for each of the types of 'Trend'.
  def stats_quantity_trend( stockPrices: List[StockPrice] ): ListMap[String, Double] = {
    val groups = stockPrices
      .groupBy(_.Trend)
      .mapValues(group =>
        group.map(_.Total_Traded_Quantity)
      )
      //// TODO: stats() + print_json() break with type: Map[String, Map[String, Either[Double, Map[String, Double]]]]
      //.mapValues(values => ListMap(
      //  "mean"   ->
      //    "median" -> grizzled.math.stats.median( values.head, values.tail:_* )
      //))

    //// Cast to: Map[String, Double]
    var quantity_trend: ListMap[String, Double] = ListMap()
    for( (key, values) <- groups ) {
      quantity_trend = quantity_trend + ( s"$key:mean"   -> grizzled.math.stats.mean(   values.head, values.tail:_* ) )
      quantity_trend = quantity_trend + ( s"$key:median" -> grizzled.math.stats.median( values.head, values.tail:_* ) )
    }
    quantity_trend
  }
}
