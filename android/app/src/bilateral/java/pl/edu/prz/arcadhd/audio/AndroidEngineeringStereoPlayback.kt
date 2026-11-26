package pl.edu.prz.arcadhd.audio

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRouting
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.min

/** Reported counts are submitted frames, not confirmed acoustic onsets. */
enum class EngineeringPlaybackEnd { SUBMITTED, REQUESTED_STOP, ROUTE_UNSAFE, AUDIO_ERROR }

data class EngineeringPlaybackReport(
    val end: EngineeringPlaybackEnd,
    val framesSubmitted: Long,
)

/**
 * Bench-only AudioTrack adapter for the optional P03 flavor.
 *
 * This class is intentionally not connected to the study/child UI. Routing
 * metadata cannot establish headphone identity, SPL, real channel isolation,
 * hearing safety or a bounded audible stop latency. No participant use is
 * authorized by this code.
 */
class AndroidEngineeringStereoPlayback(
    private val signal: EngineeringStereoSignal = EngineeringStereoSignal(),
    private val onFinished: (EngineeringPlaybackReport) -> Unit = {},
) {
    private val control = Any()
    @Volatile private var stopRequested = false
    @Volatile private var routeInvalidated = false
    @Volatile private var activeTrack: AudioTrack? = null
    private var worker: Thread? = null

    /** Only explicit, time-limited technical bench sessions are accepted. */
    fun startBenchPlayback(
        condition: StereoCondition,
        durationMs: Int,
        firstChannel: FirstChannel = FirstChannel.LEFT,
        benchAuthorized: Boolean = false,
    ) {
        require(benchAuthorized) { "bench_authorization_required" }
        require(condition != StereoCondition.QUIET) { "quiet_condition_needs_no_audio_track" }
        require(durationMs in 100..MAX_DURATION_MS)
        synchronized(control) {
            check(worker == null) { "playback_already_active" }
            val minBytes = AudioTrack.getMinBufferSize(
                signal.config.sampleRateHz,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_FLOAT,
            )
            check(minBytes > 0) { "audio_buffer_unavailable" }
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(signal.config.sampleRateHz)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minBytes, FRAMES_PER_BUFFER * BYTES_PER_STEREO_FRAME))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            if (track.state != AudioTrack.STATE_INITIALIZED || track.channelCount != 2) {
                track.release()
                error("stereo_audio_unavailable")
            }
            stopRequested = false
            routeInvalidated = false
            activeTrack = track
            val next = Thread({ run(track, condition, durationMs, firstChannel) }, "p03-audio-bench")
            worker = next
            try {
                next.start()
            } catch (failure: RuntimeException) {
                stopRequested = true
                worker = null
                activeTrack = null
                track.release()
                throw failure
            }
        }
    }

    /** Requests a stop without waiting for the writer or the callback thread. */
    fun stop() {
        stopRequested = true
        activeTrack?.let { track ->
            try {
                track.pause()
                track.flush()
            } catch (_: IllegalStateException) {
                // It may already have been released by the writer.
            }
        }
    }

    private fun run(
        track: AudioTrack,
        condition: StereoCondition,
        durationMs: Int,
        firstChannel: FirstChannel,
    ) {
        var frame = 0L
        var end: EngineeringPlaybackEnd? = null
        var listener: AudioRouting.OnRoutingChangedListener? = null
        try {
            track.play()
            val initial = describe(track.routedDevice)
            if (!EngineeringAudioRoutePolicy.accepts(initial)) {
                end = EngineeringPlaybackEnd.ROUTE_UNSAFE
                return
            }
            val verified = requireNotNull(initial)
            listener = AudioRouting.OnRoutingChangedListener { routing ->
                if (!EngineeringAudioRoutePolicy.unchanged(verified, describe(routing.routedDevice))) {
                    routeInvalidated = true
                    stop()
                }
            }
            track.addOnRoutingChangedListener(listener, Handler(Looper.getMainLooper()))
            val total = signal.config.sampleRateHz.toLong() * durationMs / 1_000L
            while (frame < total && !stopRequested) {
                if (!EngineeringAudioRoutePolicy.unchanged(verified, describe(track.routedDevice))) {
                    end = EngineeringPlaybackEnd.ROUTE_UNSAFE
                    break
                }
                val frames = min(FRAMES_PER_BUFFER.toLong(), total - frame).toInt()
                val samples = signal.render(condition, frame, frames, firstChannel)
                var written = 0
                var stallDeadline = SystemClock.elapsedRealtime() + 2_000L
                while (written < samples.size && !stopRequested) {
                    if (!EngineeringAudioRoutePolicy.unchanged(verified, describe(track.routedDevice))) {
                        end = EngineeringPlaybackEnd.ROUTE_UNSAFE
                        break
                    }
                    val count = track.write(
                        samples, written, samples.size - written, AudioTrack.WRITE_NON_BLOCKING,
                    )
                    if (count < 0 || count % 2 != 0) {
                        end = EngineeringPlaybackEnd.AUDIO_ERROR
                        break
                    }
                    if (count == 0) {
                        if (SystemClock.elapsedRealtime() >= stallDeadline) {
                            end = EngineeringPlaybackEnd.AUDIO_ERROR
                            break
                        }
                        Thread.sleep(2)
                    } else {
                        written += count
                        frame += count / 2
                        stallDeadline = SystemClock.elapsedRealtime() + 2_000L
                    }
                }
                if (written != samples.size) break
            }
            // A successful write only queues audio. Do not flush the final tail
            // until Android reports that the playback head consumed those frames.
            if (end == null && !stopRequested && !routeInvalidated) {
                val drainDeadline = SystemClock.elapsedRealtime() + 2_000L
                while (track.playbackHeadPosition.toLong() < frame && !stopRequested) {
                    if (!EngineeringAudioRoutePolicy.unchanged(verified, describe(track.routedDevice))) {
                        end = EngineeringPlaybackEnd.ROUTE_UNSAFE
                        break
                    }
                    if (SystemClock.elapsedRealtime() >= drainDeadline) {
                        end = EngineeringPlaybackEnd.AUDIO_ERROR
                        break
                    }
                    Thread.sleep(2)
                }
            }
            if (routeInvalidated) end = EngineeringPlaybackEnd.ROUTE_UNSAFE
            else if (end == null) {
                end = if (stopRequested) EngineeringPlaybackEnd.REQUESTED_STOP
                else EngineeringPlaybackEnd.SUBMITTED
            }
        } catch (_: InterruptedException) {
            end = EngineeringPlaybackEnd.REQUESTED_STOP
            Thread.currentThread().interrupt()
        } catch (_: IllegalStateException) {
            end = EngineeringPlaybackEnd.AUDIO_ERROR
        } finally {
            if (listener != null) track.removeOnRoutingChangedListener(listener)
            try {
                track.pause()
                track.flush()
            } catch (_: IllegalStateException) {
                // Playback was not initialized or has been interrupted.
            }
            track.release()
            synchronized(control) {
                if (activeTrack === track) activeTrack = null
                worker = null
            }
            onFinished(EngineeringPlaybackReport(end ?: EngineeringPlaybackEnd.AUDIO_ERROR, frame))
        }
    }

    private fun describe(device: AudioDeviceInfo?): EngineeringAudioRoute? {
        if (device == null) return null
        val kind = when (device.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> EngineeringRouteKind.WIRED_HEADPHONES
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> EngineeringRouteKind.WIRED_HEADSET
            AudioDeviceInfo.TYPE_USB_HEADSET -> EngineeringRouteKind.USB_HEADSET
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> EngineeringRouteKind.BLUETOOTH_A2DP
            else -> if (Build.VERSION.SDK_INT >= 31 &&
                device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            ) EngineeringRouteKind.BLE_HEADSET else EngineeringRouteKind.OTHER
        }
        return EngineeringAudioRoute(
            deviceId = device.id,
            kind = kind,
            isOutput = device.isSink,
            reportsStereo = device.channelCounts.contains(2),
        )
    }

    companion object {
        const val MAX_DURATION_MS = 30_000
        private const val FRAMES_PER_BUFFER = 512
        private const val BYTES_PER_STEREO_FRAME = 8
    }
}
