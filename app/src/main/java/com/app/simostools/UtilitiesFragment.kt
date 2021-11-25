package com.app.simostools

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat.startForegroundService
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController

var gUtilitiesMsgList: Array<String>? = null

class UtilitiesViewModel : ViewModel() {
    var connectionState: BLEConnectionState = BLEConnectionState.NONE
}

class UtilitiesFragment : Fragment() {
    private val TAG = "UtilitiesFragment"
    private var mArrayAdapter: ArrayAdapter<String>? = null
    private lateinit var mViewModel: FlashViewModel

    var resultPickLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            uri?.let {
                UDSFlasher.setBinFile(requireActivity().contentResolver.openInputStream(uri)!!)

                // Tell the service to start flashing
                sendServiceMessage(BTServiceTask.DO_START_FLASH.toString())

                Toast.makeText(activity, "Success", Toast.LENGTH_SHORT).show()
            }?: Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_utilities, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mViewModel = ViewModelProvider(this).get(FlashViewModel::class.java)

        mArrayAdapter = ArrayAdapter(requireContext(), R.layout.flashing_message)
        mArrayAdapter?.let { adapter ->
            gUtilitiesMsgList?.forEach {
                adapter.add(it)
            }
        }

        view.findViewById<ListView>(R.id.listViewMessage)?.let { messageBox ->
            messageBox.adapter = mArrayAdapter
            messageBox.setBackgroundColor(Color.WHITE)
        }

        val ecuInfoButton = view.findViewById<SwitchButton>(R.id.buttonGetInfo)
        ecuInfoButton.apply {
            paintBG.color = ColorList.BT_BG.value
            paintRim.color = ColorList.BT_RIM.value
            setTextColor(ColorList.BT_TEXT.value)
            setOnClickListener {
                if (mViewModel.connectionState == BLEConnectionState.CONNECTED) {
                    sendServiceMessage(BTServiceTask.DO_GET_INFO.toString())
                } else {
                    doWriteMessage("Not connected")
                }
            }
        }

        val getDTCButton = view.findViewById<SwitchButton>(R.id.buttonClearDTC)
        getDTCButton.apply {
            paintBG.color = ColorList.BT_BG.value
            paintRim.color = ColorList.BT_RIM.value
            setTextColor(ColorList.BT_TEXT.value)
            setOnClickListener {
                clickDTC(false)
            }
        }

        val clearDTCButton = view.findViewById<SwitchButton>(R.id.buttonGetDTC)
        clearDTCButton.apply {
            paintBG.color = ColorList.BT_BG.value
            paintRim.color = ColorList.BT_RIM.value
            setTextColor(ColorList.BT_TEXT.value)
            setOnClickListener {
                clickDTC(true)
            }
        }

        val backButton = view.findViewById<SwitchButton>(R.id.buttonBack)
        backButton.apply {
            paintBG.color = ColorList.BT_BG.value
            paintRim.color = ColorList.BT_RIM.value
            setTextColor(ColorList.BT_TEXT.value)
            setOnClickListener {
                findNavController().navigateUp()
            }
        }

        view.findViewById<ProgressBar>(R.id.progressBarUtilities)?.apply {
            progress = 0
            isVisible = false
            max = 100
            min = 0
            scaleY = 3F
        }

        setColor()
    }

    override fun onResume() {
        super.onResume()

        setColor()

        //Do we keep the screen on?
        view?.keepScreenOn = ConfigSettings.KEEP_SCREEN_ON.toBoolean()

        //register broadcast receiver
        val filter = IntentFilter()
        filter.addAction(GUIMessage.STATE_CONNECTION.toString())
        filter.addAction(GUIMessage.STATE_TASK.toString())
        filter.addAction(GUIMessage.UTILITY_INFO.toString())
        filter.addAction(GUIMessage.UTILITY_PROGRESS.toString())
        filter.addAction(GUIMessage.UTILITY_PROGRESS_MAX.toString())
        filter.addAction(GUIMessage.UTILITY_PROGRESS_SHOW.toString())
        activity?.registerReceiver(mBroadcastReceiver, filter)
    }

    override fun onPause() {
        super.onPause()

        //Do we keep the screen on?
        view?.keepScreenOn = false

        //unregister broadcast receiver
        activity?.unregisterReceiver(mBroadcastReceiver)
    }

    private val mBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            DebugLog.d(TAG, "Utility Fragment received action: " + intent.action.toString())
            when (intent.action) {
                GUIMessage.STATE_CONNECTION.toString()      -> mViewModel.connectionState = intent.getSerializableExtra(GUIMessage.STATE_CONNECTION.toString()) as BLEConnectionState
                GUIMessage.STATE_TASK.toString()            -> mViewModel.connectionState = BLEConnectionState.CONNECTED
                GUIMessage.UTILITY_INFO.toString()          -> doWriteMessage(intent.getStringExtra(GUIMessage.FLASH_INFO.toString())?: "")
                GUIMessage.UTILITY_INFO_CLEAR.toString()    -> doClearMessages()
                GUIMessage.UTILITY_PROGRESS.toString()      -> setProgressBar(intent.getIntExtra(GUIMessage.FLASH_PROGRESS.toString(), 0))
                GUIMessage.UTILITY_PROGRESS_MAX.toString()  -> setProgressBarMax(intent.getIntExtra(GUIMessage.FLASH_PROGRESS_MAX.toString(), 0))
                GUIMessage.UTILITY_PROGRESS_SHOW.toString() -> setProgressBarShow(intent.getBooleanExtra(GUIMessage.FLASH_PROGRESS_SHOW.toString(), false))

            }
        }
    }

    private fun clickDTC(clear: Boolean):Boolean {
        if (mViewModel.connectionState == BLEConnectionState.CONNECTED) {
            if(clear) sendServiceMessage(BTServiceTask.DO_CLEAR_DTC.toString())
            else sendServiceMessage(BTServiceTask.DO_GET_DTC.toString())
        } else {
            doWriteMessage("Not connected")
        }

        return true
    }

    private fun doClearMessages() {
        gUtilitiesMsgList = arrayOf()
        mArrayAdapter?.let {
            val btMessage = view?.findViewById<ListView>(R.id.listViewMessage)
            btMessage?.setSelection(0)
        }
    }

    private fun doWriteMessage(message: String) {
        // construct a string from the valid bytes in the buffer
        val value = gUtilitiesMsgList?: arrayOf()
        gUtilitiesMsgList = value + message
        mArrayAdapter?.let {
            it.add(message)

            val btMessage = view?.findViewById<ListView>(R.id.listViewMessage)
            btMessage?.setSelection(it.count - 1)
        }
    }

    private fun setColor() {
        //Set background color
        view?.setBackgroundColor(ColorList.BG_NORMAL.value)
    }

    private fun setProgressBar(amount: Int) {
        val pBar = view?.findViewById<ProgressBar>(R.id.progressBarUtilities)
        pBar?.progress = amount

    }

    private fun setProgressBarMax(amount: Int) {
        val pBar = view?.findViewById<ProgressBar>(R.id.progressBarUtilities)
        pBar?.max = amount
    }

    private fun setProgressBarShow(allow: Boolean) {
        val pBar = view?.findViewById<ProgressBar>(R.id.progressBarUtilities)
        pBar?.isVisible = allow
    }

    private fun sendServiceMessage(type: String) {
        activity?.let {
            val serviceIntent = Intent(it, BTService::class.java)
            serviceIntent.action = type
            startForegroundService(it, serviceIntent)
        }
    }
}
