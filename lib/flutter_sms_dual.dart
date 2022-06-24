import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class FlutterSmsDual {
  static const MethodChannel _channel = MethodChannel('flutter_sms_dual');

  Future<String> sendSMS({
    required String message,
    required List<String> recipients,
    bool sendDirect = false,
    bool sendFromDefaultSIM = true,
    String sim = "1",
  }) {
    final mapData = <dynamic, dynamic>{};
    mapData['message'] = message;
    if (!kIsWeb && Platform.isIOS) {
      mapData['recipients'] = recipients;
      return _channel
          .invokeMethod<String>('sendSMS', mapData)
          .then((value) => value ?? 'Error sending sms');
    } else {
      String _phones = recipients.join(';');
      mapData['recipients'] = _phones;
      mapData['sendDirect'] = sendDirect;
      mapData['sim'] = sim;
      mapData['sendFromDefaultSIM'] = true;
      return _channel
          .invokeMethod<String>('sendSMS', mapData)
          .then((value) => value ?? 'Error sending sms');
    }
  }

  Future<bool> canSendSMS() {
    return _channel
        .invokeMethod<bool>('canSendSMS')
        .then((value) => value ?? false);
  }

  static Future<String?> get platformVersion async {
    final String? version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }
}
