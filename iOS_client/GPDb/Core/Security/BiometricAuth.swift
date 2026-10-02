import Foundation
import LocalAuthentication

/// Face ID / Touch ID 生物识别安全锁认证服务
public final class BiometricAuthService {
    public static let shared = BiometricAuthService()

    private init() {}

    public enum BiometricType {
        case none
        case touchID
        case faceID
    }

    /// 获取当前设备支持的生物识别类型
    public var supportedBiometricType: BiometricType {
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
            return .none
        }
        switch context.biometryType {
        case .faceID: return .faceID
        case .touchID: return .touchID
        default: return .none
        }
    }

    /// 触发系统生物识别认证
    public func authenticate(reason: String = "验证身份以解锁 GPDb 私密影库") async -> Bool {
        let context = LAContext()
        context.localizedCancelTitle = "取消"

        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
            // 如果设备未设置生物识别，降级验证设备密码
            return await authenticateWithDevicePasscode(context: context, reason: reason)
        }

        do {
            return try await context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason)
        } catch {
            return false
        }
    }

    private func authenticateWithDevicePasscode(context: LAContext, reason: String) async -> Bool {
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else {
            return false
        }
        do {
            return try await context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason)
        } catch {
            return false
        }
    }
}
