package com.example.ffmpeg

import com.example.LogKeeper
import kotlinx.coroutines.delay

class FFmpegCommandBuilder {
    private val args = mutableListOf<String>()

    fun addInput(uri: String): FFmpegCommandBuilder {
        args.add("-i")
        args.add(uri)
        return this
    }

    fun setTrim(startMs: Long, endMs: Long): FFmpegCommandBuilder {
        args.add("-ss")
        args.add(String.format("%.3f", startMs / 1000f))
        args.add("-to")
        args.add(String.format("%.3f", endMs / 1000f))
        return this
    }

    fun addFilter(filter: String): FFmpegCommandBuilder {
        // Simple append for now, real implementation would chain filters
        val existingIndex = args.indexOf("-vf")
        if (existingIndex >= 0) {
             args[existingIndex + 1] = args[existingIndex + 1] + "," + filter
        } else {
            args.add("-vf")
            args.add(filter)
        }
        return this
    }
    
    fun setCrop(width: Int, height: Int, x: Int, y: Int): FFmpegCommandBuilder {
        return addFilter("crop=$width:$height:$x:$y")
    }
    
    fun setLut(lutFilePath: String): FFmpegCommandBuilder {
        return addFilter("lut3d='$lutFilePath'")
    }
    
    fun setSubtitleBurn(subtitleFilePath: String): FFmpegCommandBuilder {
        return addFilter("subtitles='$subtitleFilePath'")
    }

    fun setVideoCodec(codec: String): FFmpegCommandBuilder {
        args.add("-c:v")
        args.add(codec)
        return this
    }
    
    fun setAudioCodec(codec: String): FFmpegCommandBuilder {
        args.add("-c:a")
        args.add(codec)
        return this
    }

    fun setPreset(preset: String): FFmpegCommandBuilder {
        args.add("-preset")
        args.add(preset)
        return this
    }

    fun setCrf(crf: Int): FFmpegCommandBuilder {
        args.add("-crf")
        args.add(crf.toString())
        return this
    }

    fun addOutput(path: String): FFmpegCommandBuilder {
        args.add(path)
        return this
    }

    fun build(): String {
        return args.joinToString(" ")
    }
}

object FFmpegEngine {
    // Simulated engine execution since actual FFmpeg artifacts were unreachable in sandbox
    suspend fun execute(command: String, onProgress: (Float) -> Unit): Boolean {
        LogKeeper.d("FFmpegEngine", "Simulated Executing: ffmpeg $command")
        
        // Simulate processing over a few steps
        for(i in 1..20) {
            delay(150)
            onProgress(i / 20f)
        }
        
        LogKeeper.d("FFmpegEngine", "Execution completed.")
        return true 
    }
}
