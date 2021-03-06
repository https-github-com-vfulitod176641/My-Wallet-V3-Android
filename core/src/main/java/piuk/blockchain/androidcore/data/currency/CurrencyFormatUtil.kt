package piuk.blockchain.androidcore.data.currency

import info.blockchain.balance.CryptoValue
import info.blockchain.balance.FiatValue
import info.blockchain.balance.FormatPrecision
import info.blockchain.balance.format
import info.blockchain.balance.formatWithUnit
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale

/**
 * This class allows us to format decimal values for clean UI display.
 */
@Deprecated("Use the CryptoValue.format and formatWithUnit extension methods.")
class CurrencyFormatUtil {
    fun formatFiat(fiatValue: FiatValue): String =
        fiatValue.toStringWithoutSymbol(Locale.getDefault())

    fun formatFiatWithSymbol(fiatValue: FiatValue, locale: Locale) =
        fiatValue.toStringWithSymbol(locale)

    fun getFiatSymbol(currencyCode: String, locale: Locale): String =
        Currency.getInstance(currencyCode).getSymbol(locale)

    @Deprecated("Use format", replaceWith = ReplaceWith("cryptoValue.format(displayMode)"))
    fun format(cryptoValue: CryptoValue, displayMode: FormatPrecision = FormatPrecision.Short): String =
        cryptoValue.format(precision = displayMode)

    @Deprecated("Use format", replaceWith = ReplaceWith("cryptoValue.formatWithUnit(displayMode)"))
    fun formatWithUnit(cryptoValue: CryptoValue, displayMode: FormatPrecision = FormatPrecision.Short) =
        cryptoValue.formatWithUnit(precision = displayMode)

    @Deprecated("Use formatWithUnit", replaceWith = ReplaceWith("formatWithUnit(CryptoValue.bitcoinFromMajor(btc))"))
    fun formatBtcWithUnit(btc: BigDecimal) = formatWithUnit(CryptoValue.bitcoinFromMajor(btc))

    @Deprecated(
        "Use formatWithUnit",
        replaceWith = ReplaceWith("formatWithUnit(CryptoValue.bitcoinCashFromMajor(bch))")
    )
    fun formatBchWithUnit(bch: BigDecimal) = formatWithUnit(CryptoValue.bitcoinCashFromMajor(bch))
}
