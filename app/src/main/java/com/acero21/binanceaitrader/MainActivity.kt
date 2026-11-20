package com.acero21.binanceaitrader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var market by mutableStateOf(MarketSnapshot())
    private var cashUsd by mutableStateOf(10_000.0)
    private var btc by mutableStateOf(0.0)
    private var status by mutableStateOf("Paper trading only — no real orders are placed.")
    private var loading by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshMarket()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TradingDashboard(
                        market = market,
                        cashUsd = cashUsd,
                        btc = btc,
                        status = status,
                        loading = loading,
                        onRefresh = ::refreshMarket,
                        onBuy = ::paperBuy,
                        onSell = ::paperSell
                    )
                }
            }
        }
    }

    private fun refreshMarket() {
        if (loading) return
        loading = true
        lifecycleScope.launch {
            try {
                market = withContext(Dispatchers.IO) { fetchBtcSnapshot() }
                status = "Market data updated. Paper mode is active."
            } catch (error: Exception) {
                status = "Could not load market data. Check your connection and try again."
            } finally {
                loading = false
            }
        }
    }

    private fun paperBuy() {
        val price = market.price
        if (price <= 0.0) {
            status = "Load a valid market price before placing a paper trade."
            return
        }
        val orderUsd = 100.0
        if (cashUsd < orderUsd) {
            status = "Insufficient paper cash for the $100 test order."
            return
        }
        cashUsd -= orderUsd
        btc += orderUsd / price
        status = "Paper buy: $100 of BTC at ${price.asUsd()}. No real order was sent."
    }

    private fun paperSell() {
        val price = market.price
        if (price <= 0.0 || btc <= 0.0) {
            status = "There is no BTC in the paper portfolio to sell."
            return
        }
        val amount = minOf(btc, 100.0 / price)
        btc -= amount
        cashUsd += amount * price
        status = "Paper sell: ${amount.asBtc()} BTC at ${price.asUsd()}. No real order was sent."
    }
}

private data class MarketSnapshot(
    val price: Double = 0.0,
    val changePercent: Double = 0.0
)

private fun fetchBtcSnapshot(): MarketSnapshot {
    val connection = URL("https://api.binance.com/api/v3/ticker/24hr?symbol=BTCUSDT")
        .openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = 8_000
    connection.readTimeout = 8_000
    return try {
        if (connection.responseCode !in 200..299) error("Market API returned ${connection.responseCode}")
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(body)
        MarketSnapshot(
            price = json.getString("lastPrice").toDouble(),
            changePercent = json.getString("priceChangePercent").toDouble()
        )
    } finally {
        connection.disconnect()
    }
}

@Composable
private fun TradingDashboard(
    market: MarketSnapshot,
    cashUsd: Double,
    btc: Double,
    status: String,
    loading: Boolean,
    onRefresh: () -> Unit,
    onBuy: () -> Unit,
    onSell: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Binance AI Trader", style = MaterialTheme.typography.headlineMedium)
        Text("BTC/USDT · Public Binance market data", style = MaterialTheme.typography.bodyMedium)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bitcoin price", style = MaterialTheme.typography.titleMedium)
                Text(if (market.price > 0) market.price.asUsd() else "—", style = MaterialTheme.typography.headlineLarge)
                Text("24h change: ${"%.2f".format(Locale.US, market.changePercent)}%")
                Button(onClick = onRefresh, enabled = !loading) {
                    Text(if (loading) "Refreshing…" else "Refresh market data")
                }
            }
        }

        Card {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Paper portfolio", style = MaterialTheme.typography.titleMedium)
                Text("Cash: ${cashUsd.asUsd()}")
                Text("BTC: ${btc.asBtc()}")
                Text("Estimated total: ${(cashUsd + btc * market.price).asUsd()}")
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onBuy) { Text("Paper buy $100") }
                    Button(onClick = onSell) { Text("Paper sell $100") }
                }
            }
        }

        Text(status, style = MaterialTheme.typography.bodyMedium)
        Text(
            "Demo only. Crypto is risky. This starter app does not connect to your account, store API keys, or submit live trades.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun Double.asUsd(): String = NumberFormat.getCurrencyInstance(Locale.US).format(this)
private fun Double.asBtc(): String = "%.8f BTC".format(Locale.US, this)
