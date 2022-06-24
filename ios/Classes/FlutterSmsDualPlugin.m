#import "FlutterSmsDualPlugin.h"
#if __has_include(<flutter_sms_dual/flutter_sms_dual-Swift.h>)
#import <flutter_sms_dual/flutter_sms_dual-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "flutter_sms_dual-Swift.h"
#endif

@implementation FlutterSmsDualPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftFlutterSmsDualPlugin registerWithRegistrar:registrar];
}
@end
