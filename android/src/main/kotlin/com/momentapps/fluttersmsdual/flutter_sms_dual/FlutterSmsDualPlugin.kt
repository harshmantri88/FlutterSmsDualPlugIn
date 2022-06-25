package com.momentapps.fluttersmsdual.flutter_sms_dual

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry

/** FlutterSmsDualPlugin */
class FlutterSmsDualPlugin:  FlutterPlugin, MethodCallHandler, ActivityAware {
  private lateinit var mChannel: MethodChannel
  private var activity: Activity? = null
  private val REQUEST_CODE_SEND_SMS = 205

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onDetachedFromActivity() {
    activity = null
  }

  override fun onDetachedFromActivityForConfigChanges() {
    activity = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    setupCallbackChannels(flutterPluginBinding.binaryMessenger)
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    teardown()
  }

  private fun setupCallbackChannels(messenger: BinaryMessenger) {
    mChannel = MethodChannel(messenger, "flutter_sms_dual")
    mChannel.setMethodCallHandler(this)
  }

  private fun teardown() {
    mChannel.setMethodCallHandler(null)
  }


  companion object {
    @JvmStatic
    fun registerWith(registrar: PluginRegistry.Registrar) {
      val inst = FlutterSmsDualPlugin()
      inst.activity = registrar.activity()
      inst.setupCallbackChannels(registrar.messenger())
    }
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    when (call.method) {
      "sendSMS" -> {
        if (!canSendSMS()) {
          result.error(
            "device_not_capable",
            "The current device is not capable of sending text messages.",
            "This only applies to the ability to send text messages via iMessage, SMS, and MMS.")
          return
        }
        val message = call.argument<String?>("message") ?: ""
        val simIndex = call.argument<String?>("sim") ?: "1"
        val recipients = call.argument<String?>("recipients") ?: ""
        val sendDirect = call.argument<Boolean?>("sendDirect") ?: false
        val sendFromDefaultSIM = call.argument<Boolean?>("sendFromDefaultSIM") ?: false
        val sim = Integer.parseInt(simIndex)-1
        sendSMS(result, recipients, message, sendDirect,sendFromDefaultSIM,sim)
      }
      "canSendSMS" -> result.success(canSendSMS())
      else -> result.notImplemented()
    }
  }

  private fun canSendSMS(): Boolean {
    if (!activity!!.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY))
      return false
    val intent = Intent(Intent.ACTION_SENDTO)
    intent.data = Uri.parse("smsto:")
    val activityInfo = intent.resolveActivityInfo(activity!!.packageManager, intent.flags.toInt())
    return !(activityInfo == null || !activityInfo.exported)
  }

  private fun sendSMS(result: Result, phones: String, message: String, sendDirect: Boolean, sendFromDefaultSIM:Boolean, simIndex:Int) {
    Log.e("Android_Print","11");
    if (sendDirect && sendFromDefaultSIM) {
      Log.e("Android_Print","11.2");
      sendSMSDirectFromDefaultSIM(result, phones, message)
    } else if(sendDirect && !sendFromDefaultSIM){
      Log.e("Android_Print","11.3");
      // After LOLLIPOP we can send SMS from 2nd SIM Directly
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1){
        Log.e("Android_Print","11.4");
        sendSMSDirectFromDefaultSIM(result, phones, message)
      }else{
        Log.e("Android_Print","11.5");
        sendMessageFromSIM(result,phones, message,simIndex)
      }
    }
    else {
      sendSMSDialog(result, phones, message);
    }
  }

  private fun sendSMSDirectFromDefaultSIM(result: Result, phones: String, message: String) {
    val sentIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      PendingIntent.getBroadcast(activity, 0, Intent(
        "SMS_SENT_ACTION"
      ), PendingIntent.FLAG_UPDATE_CURRENT or
              PendingIntent.FLAG_IMMUTABLE)
    } else {
      PendingIntent.getActivity(activity, 0, Intent(
        "SMS_SENT_ACTION"
      ), PendingIntent.FLAG_ONE_SHOT)
    }
    val mSmsManager = SmsManager.getDefault()
    val numbers = phones.split(";")

    for (num in numbers) {
      if (message.toByteArray().size > 80) {
        val partMessage = mSmsManager.divideMessage(message)
        mSmsManager.sendMultipartTextMessage(num, null, partMessage, null, null)
        result.success("SMS_SENT")
      } else {
        if (num.length == 1){
          mSmsManager.sendTextMessage(num, null, message, sentIntent, null)
        }else{
          mSmsManager.sendTextMessage(num, null, message, null, null)
          result.success("SMS_SENT")
        }
      }
    }
    listenForSendIntent(result)
  }

  @SuppressLint("MissingPermission")
  private fun sendMessageFromSIM(result: Result, smsText: String, phones: String,sendFromSim :Int){

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
        val mSmsManager = SmsManager.getDefault()
        val localSubscriptionManager: SubscriptionManager = activity?.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        val localList: List<*> = localSubscriptionManager.activeSubscriptionInfoList

        val numbers = phones.split(";")

        val sentIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          PendingIntent.getBroadcast(activity, 0, Intent(
            "SMS_SENT_ACTION"
          ), PendingIntent.FLAG_UPDATE_CURRENT or
                  PendingIntent.FLAG_IMMUTABLE)
        } else {
          PendingIntent.getActivity(activity, 0, Intent(
            "SMS_SENT_ACTION"
          ), PendingIntent.FLAG_ONE_SHOT)
        }
        listenForSendIntent(result)
        for (num in numbers) {
          if (smsText.toByteArray().size > 80) {
            val partMessage = mSmsManager.divideMessage(smsText)
            mSmsManager.sendMultipartTextMessage(num, null, partMessage, null, null)
            result.success("SMS_SENT")
          } else {
            if (localSubscriptionManager.activeSubscriptionInfoCount >= 1 && sendFromSim ==0) {
              Log.e("Android_Print","5.1");
              val simInfo1 = localList[0] as SubscriptionInfo
              //SendSMS From SIM One
              if (num.length == 1){
                SmsManager.getSmsManagerForSubscriptionId(simInfo1.subscriptionId)
                  .sendTextMessage(num, null, smsText, sentIntent, null)
              }else{
                SmsManager.getSmsManagerForSubscriptionId(simInfo1.subscriptionId)
                  .sendTextMessage(num, null, smsText, null, null)
                result.success("SMS_SENT")
              }

            }else if (localSubscriptionManager.activeSubscriptionInfoCount > 1 && sendFromSim ==1){
              Log.e("Android_Print","5.2");
              val simInfo2 = localList[1] as SubscriptionInfo
              //SendSMS From SIM Two
              if (num.length == 1){
                SmsManager.getSmsManagerForSubscriptionId(simInfo2.subscriptionId)
                  .sendTextMessage(num, null, smsText, sentIntent, null)

              }else{
                SmsManager.getSmsManagerForSubscriptionId(simInfo2.subscriptionId)
                  .sendTextMessage(num, null, smsText, null, null)
                result.success("SMS_SENT")
              }

            }else{
              Log.e("Android_Print","5.3");
              result.error("UNAVAILABLE", "Requested SIM Card Not available.", null)
            }
          }
        }
      }
  }

  private fun listenForSendIntent(result: Result){
    // SEND BroadcastReceiver
    val sendSMS: BroadcastReceiver = object : BroadcastReceiver() {
      override fun onReceive(arg0: Context, arg1: Intent) {
        Log.e("sendDirectSMS","Intent   :   "+arg1.dataString+"   "+resultCode)
        when (resultCode) {
          FlutterActivity.RESULT_OK -> {
            result.success("SMS_SENT")
          }
          SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
            result.success("FAILED")
          }
          SmsManager.RESULT_ERROR_NO_SERVICE -> {
            result.success("FAILED")
          }
          SmsManager.RESULT_ERROR_NULL_PDU -> {
            result.success("FAILED")
          }
          SmsManager.RESULT_ERROR_RADIO_OFF -> {
            result.success("FAILED")

          }
          else -> {
            result.success("FAILED")
          }
        }
      }
    }
    activity?.registerReceiver(sendSMS, IntentFilter("SMS_SENT_ACTION"))
  }

  private fun sendSMSDialog(result: Result, phones: String, message: String) {
    val intent = Intent(Intent.ACTION_SENDTO)
    intent.data = Uri.parse("smsto:$phones")
    intent.putExtra("sms_body", message)
    intent.putExtra(Intent.EXTRA_TEXT, message)
    activity?.startActivityForResult(intent, REQUEST_CODE_SEND_SMS)
    result.success("SMS Sent!")
  }
}

