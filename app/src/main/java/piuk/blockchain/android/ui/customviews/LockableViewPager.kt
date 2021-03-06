package piuk.blockchain.android.ui.customviews

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.viewpager.widget.ViewPager

class LockableViewPager @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    ViewPager(context, attrs) {

    private var allowPaging: Boolean = true

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return allowPaging && super.onTouchEvent(event)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return allowPaging && super.onInterceptTouchEvent(event)
    }

    fun setPagingEnabled(enabled: Boolean) {
        this.allowPaging = enabled
    }
}