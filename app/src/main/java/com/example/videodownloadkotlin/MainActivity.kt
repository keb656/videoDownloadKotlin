package com.example.videodownloadkotlin

import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.net.URLConnection

class MainActivity : AppCompatActivity() {

    private var progressBar: ProgressDialog? = null
    internal var PERMISSIONS = arrayOf("android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE")
    private var outputFile: File? = null //파일명까지 포함한 경로
    private var path: File? = null//디렉토리경로
    //private val TAG = MainActivity::class.simpleName

    private fun hasPermissions(permissions: Array<String>): Boolean {
        var res = 0
        //스트링 배열에 있는 퍼미션들의 허가 상태 여부 확인
        for (perms in permissions) {
            res = checkCallingOrSelfPermission(perms)
            if (res != PackageManager.PERMISSION_GRANTED) {
                //퍼미션 허가 안된 경우
                return false
            }

        }
        //퍼미션이 허가된 경우
        return true
    }


    private fun requestNecessaryPermissions(permissions: Array<String>) {
        //마시멜로( API 23 )이상에서 런타임 퍼미션(Runtime Permission) 요청
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(permissions, PERMISSION_REQUEST_CODE)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        if (!hasPermissions(PERMISSIONS)) { //퍼미션 허가를 했었는지 여부를 확인
            requestNecessaryPermissions(PERMISSIONS)//퍼미션 허가안되어 있다면 사용자에게 요청
        } else {
            //이미 사용자에게 퍼미션 허가를 받음.
        }

        progressBar = ProgressDialog(this@MainActivity)
        progressBar!!.setMessage("다운로드중")
        progressBar!!.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        progressBar!!.isIndeterminate = true
        progressBar!!.setCancelable(true)

        val button = findViewById<View>(R.id.button) as Button
        button.setOnClickListener {
            //1
            val fileURL = "http://cccvlm6.myqnapcloud.com/sandartp4u/SandArtP4U_mobile_Korean.mp4"

            path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            outputFile = File(path, "SandArtP4U_mobile_Korean.mp4") //파일명까지 포함함 경로의 File 객체 생성

            if (outputFile!!.exists()) { //이미 다운로드 되어 있는 경우

                val builder = AlertDialog.Builder(this@MainActivity)
                builder.setTitle("파일 다운로드")
                builder.setMessage("이미 SD 카드에 존재합니다. 다시 다운로드 받을까요?")
                builder.setNegativeButton("아니오"
                ) { dialog, which ->
                    Toast.makeText(applicationContext, "기존 파일을 플레이합니다.", Toast.LENGTH_LONG).show()
                    playVideo(outputFile!!.path)
                }
                builder.setPositiveButton("예"
                ) { dialog, which ->
                    outputFile!!.delete() //파일 삭제

                    val downloadTask = DownloadFilesTask(this@MainActivity)
                    downloadTask.execute(fileURL)

                    progressBar!!.setOnCancelListener { downloadTask.cancel(true) }
                }
                builder.show()

            } else { //새로 다운로드 받는 경우
                val downloadTask = DownloadFilesTask(this@MainActivity)
                downloadTask.execute(fileURL)

                progressBar!!.setOnCancelListener { downloadTask.cancel(true) }
            }
        }
    }

    private inner class DownloadFilesTask(private val context: Context) : AsyncTask<String, String, Long>() {
        private var mWakeLock: PowerManager.WakeLock? = null


        //파일 다운로드를 시작하기 전에 프로그레스바를 화면에 보여줍니다.
        override fun onPreExecute() { //2
            super.onPreExecute()

            //사용자가 다운로드 중 파워 버튼을 누르더라도 CPU가 잠들지 않도록 해서
            //다시 파워버튼 누르면 그동안 다운로드가 진행되고 있게 됩니다.
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "wakelock"!!)
            mWakeLock!!.acquire()

            progressBar!!.show()
        }


        //파일 다운로드를 진행합니다.
        override fun doInBackground(vararg string_url: String): Long? { //3
            var count: Int = 0
            var FileSize: Long = -1
            var input: InputStream? = null
            var output: OutputStream? = null
            var connection: URLConnection? = null

            try {
                val url = URL(string_url[0])
                connection = url.openConnection()
                connection!!.connect()


                //파일 크기를 가져옴
                FileSize = connection.contentLength.toLong()

                //URL 주소로부터 파일다운로드하기 위한 input stream
                input = BufferedInputStream(url.openStream(), 8192)

                path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                outputFile = File(path, "SandArtP4U_mobile_Korean.mp4") //파일명까지 포함함 경로의 File 객체 생성

                // SD카드에 저장하기 위한 Output stream
                output = FileOutputStream(outputFile)


                val data = ByteArray(1024)
                var downloadedSize: Long = 0
                while (count  != -1) {

                    count = input.read(data)

                    //사용자가 BACK 버튼 누르면 취소가능
                    if (isCancelled) {
                        input.close()
                        return java.lang.Long.valueOf(-1)
                    }

                    downloadedSize += count.toLong()

                    if (FileSize > 0) {
                        val per = downloadedSize.toFloat() / FileSize * 100
                        val str = "Downloaded " + downloadedSize + "KB / " + FileSize + "KB (" + per.toInt() + "%)"
                        publishProgress("" + (downloadedSize * 100 / FileSize).toInt(), str)

                    }

                    //파일에 데이터를 기록합니다.
                    output.write(data, 0, count)
                }
                // Flush output
                output.flush()

                // Close streams
                output.close()
                input.close()


            } catch (e: Exception) {
                Log.e("Error: ", e.message)
            } finally {
                try {
                    output?.close()
                    input?.close()
                } catch (ignored: IOException) {
                }

                mWakeLock!!.release()

            }
            return FileSize
        }


        //다운로드 중 프로그레스바 업데이트
        override fun onProgressUpdate(vararg progress: String) { //4
            super.onProgressUpdate(*progress)

            // if we get here, length is known, now set indeterminate to false
            progressBar!!.isIndeterminate = false
            progressBar!!.max = 100
            progressBar!!.progress = Integer.parseInt(progress[0])
            progressBar!!.setMessage(progress[1])
        }

        //파일 다운로드 완료 후
        override fun onPostExecute(size: Long?) { //5
            super.onPostExecute(size)

            progressBar!!.dismiss()

            if (size!! > 0) {
                Toast.makeText(applicationContext, "다운로드 완료되었습니다. 파일 크기=" + size!!.toString(), Toast.LENGTH_LONG).show()

                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                mediaScanIntent.data = Uri.fromFile(outputFile)
                sendBroadcast(mediaScanIntent)

                playVideo(outputFile!!.path)

            } else
                Toast.makeText(applicationContext, "다운로드 에러", Toast.LENGTH_LONG).show()
        }

    }


    override fun onRequestPermissionsResult(permsRequestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (permsRequestCode) {

            PERMISSION_REQUEST_CODE -> if (grantResults.size > 0) {
                val readAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED
                val writeAccepted = grantResults[1] == PackageManager.PERMISSION_GRANTED

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                    if (!readAccepted || !writeAccepted) {
                        showDialogforPermission("앱을 실행하려면 퍼미션을 허가하셔야합니다.")
                        return
                    }
                }
            }
        }
    }

    private fun showDialogforPermission(msg: String) {

        val myDialog = AlertDialog.Builder(this@MainActivity)
        myDialog.setTitle("알림")
        myDialog.setMessage(msg)
        myDialog.setCancelable(false)
        myDialog.setPositiveButton("예") { arg0, arg1 ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(PERMISSIONS, PERMISSION_REQUEST_CODE)
            }
        }
        myDialog.setNegativeButton("아니오") { arg0, arg1 -> finish() }
        myDialog.show()
    }

    private fun playVideo(path: String) {
        val videoUri = Uri.fromFile(File(path))
        val videoIntent = Intent(Intent.ACTION_VIEW)
        videoIntent.setDataAndType(videoUri, "video/*")
        if (videoIntent.resolveActivity(packageManager) != null) {
            startActivity(Intent.createChooser(videoIntent, null))
        }
    }


    companion object {

        internal val PERMISSION_REQUEST_CODE = 1
    }


}