package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.data.payment.PaymentResult
import com.example.data.payment.RazorpayPayments
import com.example.data.repository.AuthRepository
import com.example.data.repository.SalonRepository
import com.example.ui.main.MainApp
import com.example.ui.theme.MyApplicationTheme
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener

class MainActivity : ComponentActivity(), PaymentResultWithDataListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Checkout.preload(applicationContext)

        val salonRepo = SalonRepository(applicationContext)
        val authRepo = AuthRepository(applicationContext)

        setContent {
            MyApplicationTheme {
                MainApp(salonRepo = salonRepo, authRepo = authRepo)
            }
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?, data: PaymentData?) {
        RazorpayPayments.publish(
            PaymentResult.Success(
                orderId = data?.orderId.orEmpty(),
                paymentId = razorpayPaymentId ?: data?.paymentId.orEmpty(),
                signature = data?.signature.orEmpty()
            )
        )
    }

    override fun onPaymentError(code: Int, response: String?, data: PaymentData?) {
        val message = when (code) {
            Checkout.PAYMENT_CANCELED -> "Payment cancelled."
            Checkout.NETWORK_ERROR -> "Network problem during payment. Please try again."
            else -> "Payment failed. Please try again."
        }
        RazorpayPayments.publish(PaymentResult.Failed(code, message))
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme { Greeting("Android") }
}
