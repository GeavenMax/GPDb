package com.gpdb.android.util

import android.util.Log
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * 健壮的 DNS 解析器 (GpdbDns)
 *
 * 针对公立 Wi-Fi / 路由器 / 私有 DNS 中可能配置的 AdGuard、CleanBrowsing、家长控制等 Sinkhole
 * （例如将 gayeroticvideoindex.com 劫持解析为 94.140.14.35 导致 TLS 握手失败）：
 * 自动识别并回退至官方 Cloudflare Anycast CDN 节点，保障后台同步与图片加载永不中断。
 */
object GpdbDns : Dns {
    private const val TAG = "GpdbDns"
    private const val GEVI_HOST = "gayeroticvideoindex.com"

    // Cloudflare Anycast 官方真实 IP 节点
    private val FALLBACK_IPS = listOf(
        byteArrayOf(104.toByte(), 26.toByte(), 7.toByte(), 5.toByte()),
        byteArrayOf(104.toByte(), 26.toByte(), 6.toByte(), 5.toByte()),
        byteArrayOf(172.toByte(), 67.toByte(), 72.toByte(), 163.toByte())
    )

    override fun lookup(hostname: String): List<InetAddress> {
        val isGevi = hostname.equals(GEVI_HOST, ignoreCase = true) || 
                     hostname.endsWith(".$GEVI_HOST", ignoreCase = true)

        if (!isGevi) {
            return Dns.SYSTEM.lookup(hostname)
        }

        try {
            val systemAddresses = Dns.SYSTEM.lookup(hostname)
            // 过滤 sinkhole / 拦截 / 欺骗 IP（例如 AdGuard Family 返回 94.140.14.35，或 0.0.0.0 / 127.0.0.1）
            val validAddresses = systemAddresses.filterNot { addr ->
                val ip = addr.hostAddress ?: ""
                ip.startsWith("94.140.") || 
                ip == "0.0.0.0" || 
                ip.startsWith("127.")
            }

            if (validAddresses.isNotEmpty()) {
                return validAddresses
            }

            Log.w(TAG, "DNS for $hostname was sinkholed to $systemAddresses. Falling back to Cloudflare Anycast nodes.")
        } catch (e: UnknownHostException) {
            Log.w(TAG, "System DNS resolution failed for $hostname: ${e.message}. Falling back to Cloudflare Anycast nodes.")
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected error resolving $hostname: ${e.message}. Falling back to Cloudflare Anycast nodes.")
        }

        return FALLBACK_IPS.map { ipBytes ->
            InetAddress.getByAddress(hostname, ipBytes)
        }
    }
}
