package eventually.client.activities

import android.graphics.Typeface
import android.os.Bundle
import android.text.style.StyleSpan
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import eventually.client.BuildConfig
import eventually.client.R
import eventually.client.activities.helpers.Common
import eventually.client.activities.helpers.Common.renderAsSpannable

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        findViewById<TextView>(R.id.app_version).text =
            getString(R.string.about_version)
                .renderAsSpannable(
                    Common.StyledString(
                        placeholder = "%1\$s",
                        content = BuildConfig.VERSION_NAME,
                        style = StyleSpan(Typeface.BOLD)
                    )
                )
    }
}
