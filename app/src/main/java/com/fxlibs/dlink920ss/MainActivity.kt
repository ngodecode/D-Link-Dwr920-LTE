package com.fxlibs.dlink920ss

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*


class MainActivity : AppCompatActivity() {

    lateinit var mHandler:Handler
    var isActive=true
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mHandler = Handler()
        chart.addSeries(series)

        chart.gridLabelRenderer.horizontalAxisTitleColor = Color.WHITE
        chart.gridLabelRenderer.verticalAxisTitleColor = Color.WHITE
        chart.gridLabelRenderer.horizontalLabelsColor = Color.WHITE
        chart.gridLabelRenderer.verticalLabelsColor = Color.WHITE

        chart.viewport.isXAxisBoundsManual = true
        chart.viewport.setMinX(0.0)
        chart.viewport.setMaxX(60.0)

        MobileAds.initialize(this)
        adView.loadAd(AdRequest.Builder().build())

        showDialogTerm()

    }

    val series: LineGraphSeries<DataPoint> = LineGraphSeries<DataPoint>()
    val data  = ArrayList<DataPoint>()
    var lastX = 0.toDouble()
    var minY = 0.0
    var maxY = 0.0
    private fun addData(signal: Double) {
        if (data.isEmpty()) {
            minY = signal - 5
            maxY = signal + 5
        }
        else {
            if (signal < minY) {
                minY = signal - 5
            }
            if (signal > maxY) {
                maxY = signal + 5
            }
        }

        lastX++
        data.add(DataPoint(lastX, signal))
        chart.viewport.isYAxisBoundsManual = true
        chart.viewport.setMinY(minY)
        chart.viewport.setMaxY(maxY)
        series.resetData(data.toArray(emptyArray()))

        if (lastX == 60.0) {
            lastX = 0.0
            data.clear()
            showRewardAds()
        }
    }

    override fun onResume() {
        super.onResume()
        isActive = true
        refresh()
    }
    override fun onPause() {
        super.onPause()
        isActive = false
    }

    override fun onDestroy() {
        super.onDestroy()
        isActive = false
    }

    private fun refresh() {
        val trustAllCerts: Array<TrustManager> = arrayOf(
            object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<X509Certificate?>?,
                    authType: String?
                ) {
                }

                override fun checkServerTrusted(
                    chain: Array<X509Certificate?>?,
                    authType: String?
                ) {
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> {
                    return arrayOf()
                }

            }
        )
        val sslContext: SSLContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())
        val client = OkHttpClient.Builder().hostnameVerifier { hostname, session -> true }
            .sslSocketFactory(sslContext.socketFactory).build()

        val retrofit = Retrofit.Builder().baseUrl("https://192.168.0.1")
                .client(client)
                .build().create(APIService::class.java)

        retrofit.getStatus(System.currentTimeMillis()).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>?, response: Response<ResponseBody>?) {
                val data = JSONObject(response?.body()?.string())
                    .getJSONObject("signal")
                    .getJSONObject("modem")

                txtOperatorName.text = data.getString("spn")
                txtServiceName.text = data.getString("service")
                txtSignal.text = data.getInt("strength").toString()

                val bars = arrayOf(bar1, bar2, bar3, bar4, bar5)
                data.getInt("level").let {
                    for (i in bars.indices) {
                        bars[i].isActivated = i < it
                    }
                }

                addData(data.getInt("strength").toDouble())

                mHandler.postDelayed({
                    if (isActive) {
                        txtSignal.text = ""
                        refresh()
                    }
                }, 1000)

            }

            override fun onFailure(call: Call<ResponseBody>?, t: Throwable?) {
                Log.e(this@MainActivity.javaClass.name, t?.message, t)
            }
        })
    }

    var rewardedAd: RewardedInterstitialAd? = null
    private fun showRewardAds() {
        RewardedInterstitialAd.load(this@MainActivity,
            resources.getString(R.string.ads_unit_reward),
            AdRequest.Builder().build(),
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    rewardedAd = ad
                    rewardedAd?.fullScreenContentCallback = object :
                        FullScreenContentCallback() {
                        /** Called when the ad failed to show full screen content.  */
                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        }

                        /** Called when ad showed the full screen content.  */
                        override fun onAdShowedFullScreenContent() {
                        }

                        /** Called when full screen content is dismissed.  */
                        override fun onAdDismissedFullScreenContent() {
                        }
                    }
                    rewardedAd?.show(this@MainActivity) { }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError?) {
                }
            })
    }

    private fun showDialogTerm() {
        if (this.getSharedPreferences("SYS", Context.MODE_PRIVATE).getBoolean("TERM_AGREE", false)) {
            return
        }

        var builder = AlertDialog.Builder(this)
        builder.setOnCancelListener {
            finish()
        }
        var view = LayoutInflater.from(this).inflate(R.layout.term_of_service, null)
        view.findViewById<TextView>(R.id.txtTerm).text = Html.fromHtml(
            getTerms(getString(R.string.app_name)).replace(
                "\n",
                ""
            )
        )
        view.findViewById<CheckBox>(R.id.chkAgree).setOnCheckedChangeListener(
            CompoundButton.OnCheckedChangeListener { btn, isChecked ->
                view.findViewById<Button>(R.id.btnNext).isEnabled = isChecked
            })
        builder.setView(view)
        var dialog = builder.create()
        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            this.getSharedPreferences("SYS", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("TERM_AGREE", true).commit()
            dialog.dismiss() }
        dialog.show()

    }

    fun getTerms(appName: String) : String {
        return "<h2> <b> Persyaratan dan Ketentuan </b> </h2>\n" +
                "<p> Selamat datang di $appName </p>\n" +
                "<p> Syarat dan ketentuan ini menguraikan aturan dan ketentuan penggunaan aplikasi $appName, yang terletak di google play store </p>\n" +
                "<p> Dengan mengakses aplikasi ini, kami menganggap Anda menerima syarat dan ketentuan ini. Jangan terus menggunakan aplikasi $appName jika Anda tidak setuju untuk mengikuti semua syarat dan ketentuan yang tercantum di halaman ini. </p>\n" +
                "<p> Terminologi berikut berlaku untuk Syarat dan Ketentuan, Pernyataan Privasi dan Pemberitahuan Sanggahan dan semua Perjanjian: \"Klien\", \"Anda\" dan \"Milik Anda\" mengacu pada Anda, orang yang menggunakan aplikasi ini dan mematuhi persyaratan Perusahaan dan kondisi. \"Perusahaan\", \"Diri Kami\", \"Kami\", \"Milik Kami\", dan \"Kami\", mengacu pada Perusahaan kami. \"Pihak\", \"Pihak\", atau \"Kami\", mengacu pada Klien dan diri kami sendiri. Semua istilah mengacu pada penawaran, penerimaan dan pertimbangan pembayaran yang diperlukan untuk melakukan proses bantuan kami kepada Klien dengan cara yang paling tepat untuk tujuan yang jelas untuk memenuhi kebutuhan Klien sehubungan dengan penyediaan layanan yang dinyatakan Perusahaan, sesuai dengan dan tunduk pada, hukum yang berlaku di Indonesia. Setiap penggunaan terminologi di atas atau kata lain dalam bentuk tunggal, jamak, huruf besar dan / atau dia, dianggap dapat dipertukarkan dan oleh karena itu merujuk pada yang sama.\n" +
                "\n" +
                "<h3><li><b>Paket Data Internet</b></i></h3>\n" +
                "<p> Kami menggunakan penggunaan paket data internet. Dengan mengakses $appName, Anda setuju untuk memperbolehkan kami menggunakan paket data internet sesuai dengan Kebijakan Privasi $appName. </p>\n" +
                "<p> Aplikasi ini membutuhkan koneksi internet untuk dapat menampilkan iklan</p>\n" +
                "\n" +
                "<h3><li><b>Modem Wifi Information</b></i></h3>\n" +
                "<p> Kami menggunakan informasi dari modem wifi yang Anda pakai. Dengan mengakses $appName, Anda setuju untuk memperbolehkan kami menggunakan informasi modem sesuai dengan Kebijakan Privasi $appName. </p>\n" +
                "<p> Aplikasi ini membutuhkan informasi modem untuk dapat menampilkan chart dan graph</p>\n" +
                "\n" +
                "<h3><li><b>Local Preference</b></i> </h3>\n" +
                "<p> Kami menggunakan penggunaan local preference. Dengan mengakses $appName, Anda setuju untuk memperbolehkan kami menggunakan lokal preference sesuai dengan Kebijakan Privasi $appName. </p>\n" +
                "<p> Lokal preference digunakan untuk mengingat input pengguna untuk setiap penggunaan layanan, dengan begitu pengguna tidak perlu repot untuk mengisi kembali saat akan digunakan pada sesi berikutnya. </p>\n" +
                "\n" +
                "<h3><li><b>License</b></i> </h3>\n" +
                "<p> $appName dan / atau pemberi lisensinya memiliki hak kekayaan intelektual untuk semua materi di $appName. Semua hak kekayaan intelektual dilindungi. Anda dapat mengakses ini dari $appName untuk penggunaan pribadi dan Anda dengan tunduk pada batasan yang ditetapkan dalam syarat dan ketentuan ini. </p>\n" +
                "\n" +
                "<p> <b>Anda tidak diperbolehkan untuk:</b> </p>\n" +
                "<ul>\n" +
                "    <li> Publikasikan ulang materi dari $appName </li>" +
                "    <li> Menjual, menyewakan atau mensublisensikan materi dari $appName </li>\n" +
                "    <li> Mereproduksi, menggandakan, atau menyalin materi dari $appName </li>\n" +
                "    <li> Mendistribusikan kembali konten dari $appName </li>\n" +
                "</ul>\n" +
                "\n" +
                "<p> <b>Perjanjian ini akan dimulai pada tanggal Perjanjian ini.</b> </p>\n" +
                "\n" +
                "<p> Bagian dari aplikasi ini menawarkan kesempatan bagi pengguna untuk mendapatkan informasi mengenai kekuatan sinyal dari modem secara realtime. Kami tidak akan bertanggung jawab atas Komentar atau kewajiban, kerusakan atau biaya yang disebabkan dan / atau diderita sebagai akibat dari penggunaan dan / atau tampilan dari aplikasi ini. </p>\n" +
                "\n" +
                "<p> <b>Anda menjamin dan menyatakan bahwa:</b> </p>\n" +
                "\n" +
                "<ul>\n" +
                "<li> Memperbolehkan akses penggunaan paket data internet untuk penggunaan aplikasi </li>\n" +
                "<li> Memperbolehkan akses penggunaan informasi perangkat modem </li>\n" +
                "</ul>\n" +
                "\n" +
                "<p><b>Dengan ini Anda memberi $appName lisensi non-eksklusif untuk menggunakan, mereproduksi, mengedit, dan mengizinkan orang lain untuk menggunakan, mereproduksi, dan mengedit informasi yang anda masukan kedalam aplikasi dalam segala bentuk, format, atau media. </b></p>\n" +
                "\n"
    }
}