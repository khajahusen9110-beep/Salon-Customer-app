package com.example.data.payment

import android.app.Activity
import com.example.data.model.PaymentOrder
import com.razorpay.Checkout
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject

/** Outcome of one Razorpay Checkout session. */
sealed class PaymentResult {
    data class Success(val orderId: String, val paymentId: String, val signature: String) : PaymentResult()
    data class Failed(val code: Int, val message: String) : PaymentResult()
}

/**
 * Opens Razorpay Checkout and relays its result. Razorpay reports back to the Activity
 * (MainActivity implements PaymentResultWithDataListener), which forwards it here.
 */
object RazorpayPayments {
    private val _results = MutableSharedFlow<PaymentResult>(extraBufferCapacity = 1)
    val results: SharedFlow<PaymentResult> = _results.asSharedFlow()

    fun publish(result: PaymentResult) {
        _results.tryEmit(result)
    }

    fun open(activity: Activity, order: PaymentOrder, description: String, holdSecondsLeft: Long) {
        val options = JSONObject().apply {
            put("key", order.keyId)
            put("order_id", order.orderId)
            put("amount", order.amountPaise)
            put("currency", order.currency)
            put("name", order.salonName.ifBlank { "QFree Salon" })
            put("description", description)
            put("theme", JSONObject().put("color", "#0F172A"))
            put("prefill", JSONObject().apply {
                if (order.prefillName.isNotBlank()) put("name", order.prefillName)
                if (order.prefillContact.isNotBlank()) put("contact", order.prefillContact)
                if (order.prefillEmail.isNotBlank()) put("email", order.prefillEmail)
            })
            // Close checkout before the slot hold runs out (Razorpay minimum is 1 minute).
            put("timeout", holdSecondsLeft.coerceIn(60, 900))
            put("retry", JSONObject().put("enabled", true).put("max_count", 3))
        }
        val checkout = Checkout()
        checkout.setKeyID(order.keyId)
        checkout.open(activity, options)
    }
}
