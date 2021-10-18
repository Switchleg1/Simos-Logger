package com.app.simoslogger

import android.content.Context
import java.io.*
import java.lang.Long.parseLong

object PIDCSVFile {
    private val TAG = "PIDCSVFile"

    fun read(fileName: String?, context: Context?, addressMin: Long, addressMax: Long):Array<PIDStruct?>? {
        context?.let {
            DebugLog.i(TAG, "reading $fileName.")

            //get file path and check if file exists
            val path = it.getExternalFilesDir("")
            val csvFile = File(path, "/$fileName")
            if (!csvFile.exists()) {
                DebugLog.i(TAG, "file does not exist.")
                return null
            }

            return readStream(FileInputStream(csvFile), addressMin, addressMax)
        }

        return null
    }

    fun readStream(fileStream: InputStream?, addressMin: Long, addressMax: Long):Array<PIDStruct?>? {
        //get stream
        val inStream = BufferedReader(InputStreamReader(fileStream))

        //is the file empty?
        if(!inStream.ready()) {
            //close file and return
            inStream.close()
            DebugLog.i(TAG, "file is empty.")
            return null
        }

        //check header
        val cfgLine = inStream.readLine()
        if(cfgLine == CSV_CFG_LINE + "\n") {
            //close file and return
            inStream.close()
            DebugLog.i(TAG, "config line does not match")
            return null
        }

        //read PIDS
        var i = 0
        var pidList: Array<PIDStruct?>? = null
        while(inStream.ready() && i < MAX_PIDS) {
            var pidString = inStream.readLine()
            val pidStrings: Array<String?> = arrayOfNulls(CSV_VALUE_COUNT)

            var d = 0
            while(d < CSV_VALUE_COUNT-1 && pidString != pidString.substringBefore(",")) {
                pidStrings[d] = pidString.substringBefore(",")
                pidString = pidString.substringAfter(",")
                DebugLog.d(TAG, "PID $i,$d: ${pidStrings[d]}")
                d++
            }
            pidStrings[d] = pidString
            DebugLog.d(TAG, "PID $i,$d: ${pidStrings[d]}")
            d++

            if(d == CSV_VALUE_COUNT) {
                try {
                    //make room
                    pidList = pidList?.copyOf(i+1) ?: arrayOfNulls(1)

                    //make sure the length is legal
                    if(pidStrings[5]!!.toInt() != 1 && pidStrings[5]!!.toInt() != 2 && pidStrings[5]!!.toInt() != 4)
                        throw RuntimeException("Unexpected pid length: ${pidStrings[5]!!}")

                    //convert address and error check
                    val l = parseLong(pidStrings[4]!!.substringAfter("0x"), 16)
                    if(l < addressMin)
                        throw Exception("Unexpected address 0x${l.toHex()}, min address 0x${addressMin.toHex()}")
                    if(l > addressMax)
                        throw Exception("Unexpected address 0x${l.toHex()}, max address 0x${addressMax.toHex()}")

                    //Build did
                    pidList[i++] = PIDStruct(l,
                        pidStrings[5]!!.toInt(),
                        pidStrings[6]!!.toBoolean(),
                        pidStrings[7]!!.toFloat(),
                        pidStrings[8]!!.toFloat(),
                        pidStrings[9]!!.toFloat(),
                        pidStrings[10]!!.toFloat(),
                        pidStrings[11]!!.toFloat(),
                        0.0f,
                        pidStrings[2]!!,
                        pidStrings[3]!!,
                        pidStrings[0]!!,
                        pidStrings[1]!!)
                } catch(e: Exception) {
                    //close file and return
                    inStream.close()
                    DebugLog.e(TAG, "unable to create PIDStructure ${pidList?.count()}", e)
                    DebugLog.i(TAG, "failed.")
                    return null
                }
            } else {
                //close file and return
                inStream.close()
                DebugLog.i(TAG, "failed.")
                return null
            }
        }

        //close file and return
        inStream.close()
        DebugLog.i(TAG, "successful.")
        return pidList
    }

    fun write(fileName: String?, context: Context?, pidList: Array<PIDStruct?>?, overWrite: Boolean): Boolean {
        pidList?.let { list ->
            context?.let {
                DebugLog.i(TAG, "writing $fileName.")

                //get filename and check if it exists
                val path = it.getExternalFilesDir("")
                val csvFile = File(path, "/$fileName")
                if (csvFile.exists()) {
                    if (overWrite) {
                        DebugLog.i(TAG, "overwriting file.")
                        csvFile.delete()
                    } else {
                        DebugLog.i(TAG, "file exists, not allowed to overwrite.")
                        return false
                    }
                }

                //Create new file
                csvFile.createNewFile()
                val outStream = FileOutputStream(csvFile)

                //write header
                outStream.write((CSV_CFG_LINE + "\n").toByteArray())

                //write pids
                for (i in 0 until list.count()) {
                    val did = list[i]
                    did?.let {
                        try {
                            val addressString =
                                if ((did.address.toInt() and 0xFFFF0000.toInt()) != 0) {
                                    "0x${did.address.toInt().toHex()}"
                                } else {
                                    "0x${did.address.toShort().toHex()}"
                                }

                            val writeString = "${did.name},${did.unit}," +
                                    "${did.equation},${did.format}," +
                                    "${addressString},${did.length}," +
                                    "${did.signed},${did.progMin}," +
                                    "${did.progMax},${did.warnMin}," +
                                    "${did.warnMax},${did.smoothing}"

                            outStream.write((writeString + "\n").toByteArray())
                        } catch(e: Exception) {
                            //close file and return
                            outStream.close()
                            DebugLog.e(TAG, "unable to write PID", e)
                            DebugLog.i(TAG, "failed.")
                            return false
                        }
                    }
                }

                //close file and return
                outStream.close()

                DebugLog.i(TAG, "successful.")
                return true
            }
        }

        return false
    }
}