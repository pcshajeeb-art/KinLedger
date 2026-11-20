package com.example.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Commute
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class IconChoice(
    val key: String,
    val label: String,
    val icon: ImageVector
)

object IconRegistry {
    val accountIcons = listOf(
        IconChoice("wallet", "Cash Wallet", Icons.Rounded.AccountBalanceWallet),
        IconChoice("bank", "Bank", Icons.Rounded.AccountBalance),
        IconChoice("payments", "UPI / Card", Icons.Rounded.Payments),
        IconChoice("savings", "Savings", Icons.Rounded.Savings),
        IconChoice("home", "Household", Icons.Rounded.Home)
    )

    val categoryIcons = listOf(
        IconChoice("shopping_cart", "Grocery", Icons.Rounded.ShoppingCart),
        IconChoice("restaurant", "Dining", Icons.Rounded.Restaurant),
        IconChoice("card_giftcard", "Gift", Icons.Rounded.CardGiftcard),
        IconChoice("medical_services", "Medical", Icons.Rounded.LocalHospital),
        IconChoice("receipt_long", "Bills", Icons.Rounded.ReceiptLong),
        IconChoice("category", "Misc", Icons.Rounded.Category),
        IconChoice("currency_exchange", "Remittance", Icons.Rounded.CurrencyExchange),
        IconChoice("savings", "Income", Icons.Rounded.Savings),
        IconChoice("attach_money", "Refund", Icons.Rounded.AttachMoney),
        IconChoice("home", "Home", Icons.Rounded.Home),
        IconChoice("bolt", "Power", Icons.Rounded.Bolt),
        IconChoice("commute", "Travel", Icons.Rounded.Commute),
        IconChoice("directions_car", "Fuel", Icons.Rounded.DirectionsCar),
        IconChoice("school", "Education", Icons.Rounded.School),
        IconChoice("phone", "Mobile", Icons.Rounded.PhoneAndroid),
        IconChoice("shopping_bag", "Shopping", Icons.Rounded.ShoppingBag),
        IconChoice("flight", "Flight", Icons.Rounded.Flight)
    )

    private val allIconsMap: Map<String, ImageVector> =
        (accountIcons + categoryIcons).associate { it.key to it.icon }

    fun resolveIcon(key: String): ImageVector =
        allIconsMap[key] ?: Icons.Rounded.Category

    val colorPalette: List<Long> = listOf(
        0xFF0D9488L, // Deep Teal
        0xFF0F766EL, // Dark Teal
        0xFF059669L, // Emerald
        0xFFE11D48L, // Coral Rose
        0xFFEF4444L, // Red
        0xFFD97706L, // Amber
        0xFF8B5CF6L, // Violet
        0xFF2563EBL, // Blue
        0xFF0284C7L, // Sky
        0xFF64748BL  // Slate
    )

    fun colorFromLong(argb: Long): Color = Color(argb.toInt())
}
