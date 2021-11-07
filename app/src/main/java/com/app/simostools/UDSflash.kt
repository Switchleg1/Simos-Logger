package com.app.simostools

import java.io.InputStream
import java.lang.Math.round

object UDSFlasher {
    private val TAG = "UDSflash"
    private var mTask = FLASH_ECU_CAL_SUBTASK.NONE
    private var mCommand: ByteArray = byteArrayOf()
    private var mLastString: String = ""
    private var flashConfirmed: Boolean = false
    private var cancelFlash: Boolean = false
    private var bin: ByteArray = byteArrayOf()
    private var ecuAswVersion: ByteArray = byteArrayOf()
    private var transferSequence = -1
    private var progress = 0

    fun getSubtask(): FLASH_ECU_CAL_SUBTASK{
        return mTask
    }

    fun getFlashConfirmed(): Boolean{
        return flashConfirmed
    }

    fun setFlashConfirmed(input: Boolean = false){
        flashConfirmed = input
    }

    fun cancelFlash(){
        cancelFlash = true
    }

    fun getInfo(): String {
        val response = mLastString
        //mLastString = ""
        return response
    }

    fun getCommand(): ByteArray {
        val response = mCommand
        //mCommand = byteArrayOf()
        return response
    }

    fun started(): Boolean {
        return !(mTask == FLASH_ECU_CAL_SUBTASK.NONE)
    }

    fun getProgress(): Int{
        return progress
    }

    fun setBinFile(input: InputStream) {
        DebugLog.d(TAG, "Received BIN stream from GUI")
        mTask = FLASH_ECU_CAL_SUBTASK.NONE
        flashConfirmed = false
        cancelFlash = false
        bin =  input.readBytes()
    }

    fun startTask(ticks: Int): ByteArray {

        if(bin.size < 500000){
            mLastString = "Selected file too small..."
            return byteArrayOf()
        }
        else if(bin.size > 500000 && bin.size < 4000000){
            //Read box code from ECU
            mTask = FLASH_ECU_CAL_SUBTASK.GET_ECU_BOX_CODE

            DebugLog.d(TAG, "Initiating Calibration Flash subroutine: " + mTask.toString())
            mLastString = "Initiating calibration flash routines"
            return UDS_COMMAND.READ_IDENTIFIER.bytes + ECUInfo.PART_NUMBER.address
        }
        else{
            //It's a full bin flash....
            mLastString = "Full flash isn't implemented yet"
            return byteArrayOf()
        }
    }
    
    @Synchronized
    fun processFlashCAL(ticks: Int, buff: ByteArray?): UDSReturn {

        buff?.let {


            DebugLog.d(TAG, "Flash subroutine: " + mTask)
            if(checkResponse(buff) == UDS_RESPONSE.NEGATIVE_RESPONSE){
                //DebugLog.w(TAG,"Negative response received from ECU!")
                //mCommand = sendTesterPresent()
                //return UDSReturn.COMMAND_QUEUED
            }

            when(mTask){
                FLASH_ECU_CAL_SUBTASK.GET_ECU_BOX_CODE ->{

                    //If we can't get a good response from the ECU, we'll
                    // Skip to the force option
                    //if(....){
                    //    mLastString = "NO VALID RESPONSE, FORCE FLASH???\n" +
                    //            "NO INTEGRITY CHECK POSSIBLE!!!"
                    //    mTask = FLASH_ECU_CAL_SUBTASK.CLEAR_DTC
                    //}


                    //If we're in here with a response to our PID request
                    when(checkResponse(buff)){

                        UDS_RESPONSE.READ_IDENTIFIER ->{
                            ecuAswVersion = buff.copyOfRange(3, buff.size)
                            DebugLog.d(TAG, "Received ASW version ${ecuAswVersion.toHex()} from ecu")

                            mLastString = "Read box code from ECU: " + String(ecuAswVersion)
                            mTask = mTask.next()

                            mCommand = UDS_COMMAND.TESTER_PRESENT.bytes
                            return UDSReturn.COMMAND_QUEUED
                        }

                        UDS_RESPONSE.POSITIVE_RESPONSE -> {
                            mCommand = UDS_COMMAND.READ_IDENTIFIER.bytes + ECUInfo.PART_NUMBER.address
                            mLastString = "Initiating flash routines"
                            return UDSReturn.COMMAND_QUEUED
                        }
                        else -> {
                            DebugLog.d(TAG, "Error with ECU Response: " + buff.toHex())
                            mLastString = "Error with ECU Response: " + String(buff)
                            return UDSReturn.ERROR_UNKNOWN
                        }

                    }

                }

                FLASH_ECU_CAL_SUBTASK.CHECK_FILE_COMPAT -> {

                    val binAswVersion = bin.copyOfRange(0x60, 0x6B)
                    //Hard code an ASW version that won't work so it won't flash!
                    //val binAswVersion = byteArrayOf(0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte())

                    //Compare the two strings:
                    if (String(ecuAswVersion).trim() != String(binAswVersion).trim()) {
                        DebugLog.d(TAG,"ECU software version: ${ecuAswVersion.toHex()}, and file" +
                                " software version: ${binAswVersion.toHex()}")
                        return UDSReturn.ERROR_RESPONSE
                    }

                    mLastString = mTask.toString() + "\nBox code on selected BIN file: " + String(binAswVersion) +
                            "\nPlease confirm flash procedure"
                    mTask = mTask.next()
                    mCommand = UDS_COMMAND.TESTER_PRESENT.bytes
                    return UDSReturn.COMMAND_QUEUED
                }

                FLASH_ECU_CAL_SUBTASK.CONFIRM_PROCEED -> {
                    mLastString = ""
                    if(cancelFlash){
                        mLastString = "Flash has been canceled"
                        bin = byteArrayOf()
                        mTask = FLASH_ECU_CAL_SUBTASK.NONE
                        return UDSReturn.ABORTED
                    }

                    if(!flashConfirmed){

                        return UDSReturn.FLASH_CONFIRM
                    }
                    else{
                        mLastString = "Flash confirmed! Proceeding"
                        mTask = mTask.next()
                        mCommand = UDS_COMMAND.TESTER_PRESENT.bytes
                        return UDSReturn.OK
                    }
                }

                FLASH_ECU_CAL_SUBTASK.CHECKSUM_BIN ->{
                    mLastString = mTask.toString() + "\n"

                    var checksummed = FlashUtilities.checksumSimos18(bin)
                    mLastString += "Original checksum: " + checksummed.fileChecksum + "\n"
                    mLastString += "Calculatated checksum: " + checksummed.calculatedChecksum + "\n"
                    if(checksummed.updated) mLastString += "    Checksum corrected\n"
                    else mLastString += "    Checksum not updated\n"

                    checksummed = FlashUtilities.checksumECM3(checksummed.bin)
                    mLastString += "Original ECM3: " + checksummed.fileChecksum + "\n"
                    mLastString += "    Calculated ECM3: " + checksummed.calculatedChecksum + "\n"
                    if(checksummed.updated) mLastString += "Checksum corrected\n"
                    else mLastString += "    Checksum not updated\n"

                    bin = checksummed.bin

                    mTask = mTask.next()
                    mCommand = UDS_COMMAND.TESTER_PRESENT.bytes
                    return UDSReturn.COMMAND_QUEUED
                }

                FLASH_ECU_CAL_SUBTASK.COMPRESS_BIN ->{
                    mLastString = mTask.toString() + "\n"

                    var uncompressedSize = bin.size
                    bin = FlashUtilities.encodeLZSS(bin)

                    var compressedSize = bin.size

                    mLastString += "Uncompressed bin size: $uncompressedSize\n"
                    mLastString += "Compressed bin size: $compressedSize"

                    mTask = mTask.next()
                    mCommand = UDS_COMMAND.TESTER_PRESENT.bytes
                    return UDSReturn.COMMAND_QUEUED

                }

                FLASH_ECU_CAL_SUBTASK.ENCRYPT_BIN -> {
                    mLastString = mTask.toString() + "\n"
                    var unencryptedSize = bin.size

                    bin = FlashUtilities.encrypt(bin, SIMOS18_AES_KEY, SIMOS18_AES_IV)

                    var encryptedSize = bin.size

                    mLastString += "Unencrypted bin size: $unencryptedSize \n"
                    mLastString += "Encrypted bin size: $encryptedSize"

                    if(bin.isEmpty()){
                        mLastString = "Error encrypting BIN"
                        return UDSReturn.ERROR_UNKNOWN
                    }

                    DebugLog.d(TAG, bin.copyOfRange(0, CAL_BLOCK_TRANSFER_SIZE * 2).toHex())

                    mTask = mTask.next()
                    mCommand = UDS_COMMAND.TESTER_PRESENT.bytes
                    return UDSReturn.COMMAND_QUEUED
                }

                FLASH_ECU_CAL_SUBTASK.CLEAR_DTC -> {
                    //We should enter this function after a 3e response
                    when(checkResponse(buff)){
                        UDS_RESPONSE.EXTENDED_DIAG_ACCEPTED -> {
                            mCommand = UDS_COMMAND.TESTER_PRESENT.bytes
                            mLastString = "Extended diagnostic 03 accepted"
                            mTask = mTask.next()
                            return UDSReturn.COMMAND_QUEUED
                        }
                        UDS_RESPONSE.CLEAR_DTC_SUCCESSFUL -> {
                            mCommand = (UDS_COMMAND.EXTENDED_DIAGNOSTIC.bytes) + byteArrayOf(0x03.toByte())
                            mLastString = "Entering extended diagnostic 03"
                            return UDSReturn.COMMAND_QUEUED
                        }
                        UDS_RESPONSE.NEGATIVE_RESPONSE ->{
                            mCommand = byteArrayOf()
                            mLastString = "Waiting for CLEAR DTC successful"
                            return UDSReturn.OK
                        }
                        UDS_RESPONSE.POSITIVE_RESPONSE ->{
                            DebugLog.d(TAG,"Received " + buff.toHex())

                            mLastString = mTask.toString()
                            return UDSReturn.CLEAR_DTC_REQUEST
                        }
                        else -> {
                            mCommand = byteArrayOf()
                            return UDSReturn.OK
                        }
                    }

                }

                FLASH_ECU_CAL_SUBTASK.CHECK_PROGRAMMING_PRECONDITION -> {

                    when(checkResponse(buff)) {
                        UDS_RESPONSE.ROUTINE_ACCEPTED -> {
                            //Open extended diagnostic session
                            mCommand = UDS_COMMAND.EXTENDED_DIAGNOSTIC.bytes + byteArrayOf(0x02.toByte())
                            mLastString = "Entering extended diagnostics 02"
                            mTask = mTask.next()
                            return UDSReturn.COMMAND_QUEUED
                        }

                        else -> {
                            //Check programming precondition, routine 0x0203
                            mCommand = UDS_COMMAND.START_ROUTINE.bytes + UDS_ROUTINE.CHECK_PROGRAMMING_PRECONDITION.bytes
                            mLastString = mTask.toString()
                            return UDSReturn.COMMAND_QUEUED
                        }
                    }

                }

                FLASH_ECU_CAL_SUBTASK.OPEN_EXTENDED_DIAGNOSTIC -> {
                    when(checkResponse(buff)) {

                        UDS_RESPONSE.NEGATIVE_RESPONSE -> {
                            //mCommand = UDS_COMMAND.TESTER_PRESENT.bytes
                            mLastString = "Waiting for Seed"
                            //return UDSReturn.COMMAND_QUEUED
                            return UDSReturn.OK
                        }

                        UDS_RESPONSE.EXTENDED_DIAG_ACCEPTED -> {
                            if(buff[1] == 0x02.toByte()){
                                mCommand = UDS_COMMAND.SECURITY_ACCESS.bytes + byteArrayOf(0x11.toByte())
                                mLastString = "Asking for seedkey exhange"
                                mTask = mTask.next()
                                return UDSReturn.COMMAND_QUEUED
                            }
                        }

                        else -> {
                            return UDSReturn.ERROR_UNKNOWN
                        }

                    }


                }
                

                FLASH_ECU_CAL_SUBTASK.SA2SEEDKEY -> {
                    //Pass SA2SeedKey unlock_security_access(17)
                    when(checkResponse(buff)){
                        UDS_RESPONSE.SECURITY_ACCESS_GRANTED -> {
                            if(buff[1] == 0x11.toByte()){
                                var challenge = buff.copyOfRange(2,buff.size)

                                var vs = FlashUtilities.Sa2SeedKey(VW_SEEDKEY_TAPE, challenge)
                                var response = vs.execute()

                                mCommand = UDS_COMMAND.SECURITY_ACCESS.bytes + byteArrayOf(0x12.toByte()) + response
                                mLastString = "Passing SeedKey challenege"
                                return UDSReturn.COMMAND_QUEUED
                            }
                            else if(buff[1] == 0x12.toByte()){
                                mLastString = "Passed SeedKey Challenege"
                                mCommand = UDS_COMMAND.TESTER_PRESENT.bytes
                                mTask = mTask.next()
                                return UDSReturn.COMMAND_QUEUED
                            }
                        }
                        else ->{
                            mLastString = ""
                            return UDSReturn.OK
                        }
                    }

                }
                FLASH_ECU_CAL_SUBTASK.WRITE_WORKSHOP_LOG -> {
                    when(checkResponse(buff)){
                        UDS_RESPONSE.POSITIVE_RESPONSE -> {
                            //Write workshop tool log
                            //  0x 2E 0xF15A = 0x20, 0x7, 0x17, 0x42,0x04,0x20,0x42,0xB1,0x3D,
                            mCommand = byteArrayOf(0x2E.toByte(),
                                0xF1.toByte(), 0x5A.toByte(), 0x20.toByte(), 0x07.toByte(), 0x17.toByte(),
                                0x42.toByte(),0x04.toByte(),0x20.toByte(),0x42.toByte(),0xB1.toByte(),0x3D.toByte())
                            mLastString = "Writing workshop code"
                            return UDSReturn.COMMAND_QUEUED
                        }
                        UDS_RESPONSE.WRITE_IDENTIFIER_ACCEPTED -> {
                            if(buff[1] == 0xF1.toByte() && buff[2] == 0x5A.toByte()) {
                                mLastString = "Wrote workshop code"
                                mCommand = UDS_COMMAND.TESTER_PRESENT.bytes
                                mTask = mTask.next()
                                return UDSReturn.COMMAND_QUEUED
                            }
                        }
                        else -> {
                            return UDSReturn.ERROR_UNKNOWN
                        }
                    }

                }
                FLASH_ECU_CAL_SUBTASK.FLASH_BLOCK -> {
                    when(checkResponse(buff)){
                        //We should enter here from a tester present.
                        UDS_RESPONSE.POSITIVE_RESPONSE -> {
                            //erase block: 31 01 FF 00 01 05
                            mCommand = UDS_COMMAND.START_ROUTINE.bytes + byteArrayOf(0xFF.toByte(),0x00.toByte(),0x01.toByte(),0x05.toByte())
                            mLastString = "Erasing CAL block to prepare for flashing"
                            return UDSReturn.COMMAND_QUEUED
                        }
                        //We should have a 71 in response to the erase command we just sent....
                        UDS_RESPONSE.ROUTINE_ACCEPTED -> {
                            //Request download 34 AA 41 05 00 07 FC 00
                            mCommand = byteArrayOf(0x34.toByte(),0xAA.toByte(),0x41.toByte(),0x05.toByte(),0x00.toByte(),0x07.toByte(), 0xFC.toByte(), 0x00.toByte())
                            mLastString = "Requesting block download"
                            return UDSReturn.COMMAND_QUEUED
                        }
                        UDS_RESPONSE.DOWNLOAD_ACCEPTED -> {
                            transferSequence = 1
                            progress = round(transferSequence.toFloat() / (bin.size / CAL_BLOCK_TRANSFER_SIZE) * 100)

                            //Send bytes, 0x36 [frame number]
                            //Break the whole bin into frames of FFD size, and
                            // we'll use that array.
                            mCommand = UDS_COMMAND.TRANSFER_DATA.bytes +  byteArrayOf(transferSequence.toByte()) + bin.copyOfRange(0, CAL_BLOCK_TRANSFER_SIZE)
                            mLastString = "Transfer Started"

                            return UDSReturn.COMMAND_QUEUED
                        }
                        UDS_RESPONSE.TRANSFER_DATA_ACCEPTED -> {
                            val totalFrames: Int = bin.size / CAL_BLOCK_TRANSFER_SIZE


                            //If the last frame we sent was acked, increment the transfer counter
                            // set the progress bar.  Check to see if we're at the total number
                            // of frames that we should be (and if we are, request an exit from
                            // the transfer
                            if(buff[1] == transferSequence.toByte()){
                                transferSequence++
                                progress = round(transferSequence.toFloat() / (bin.size / CAL_BLOCK_TRANSFER_SIZE) * 100)

                                mLastString = ""
                                //if the current transfer sequence number is larger than the max
                                // number that we need for the payload, send a 'transfer exit'
                                if(transferSequence > totalFrames + 1){
                                    mCommand = UDS_COMMAND.TRANSFER_EXIT.bytes

                                    return UDSReturn.COMMAND_QUEUED
                                }
                            }

                            //otherwise, we get here
                            // start is frame size + transfer sequence
                            // end is start + frame size *OR* the end of the bin
                            var start = CAL_BLOCK_TRANSFER_SIZE * (transferSequence - 1)
                            var end = start + CAL_BLOCK_TRANSFER_SIZE
                            if(end > bin.size) end = bin.size

                            mCommand = UDS_COMMAND.TRANSFER_DATA.bytes + byteArrayOf(transferSequence.toByte()) + bin.copyOfRange(start, end)
                            return UDSReturn.COMMAND_QUEUED
                        }

                        UDS_RESPONSE.TRANSFER_EXIT_ACCEPTED -> {
                            progress = 0
                            mCommand = UDS_COMMAND.TESTER_PRESENT.bytes
                            mTask = mTask.next()
                            mLastString = "Transfer Done"
                            return UDSReturn.COMMAND_QUEUED
                        }

                        UDS_RESPONSE.NEGATIVE_RESPONSE -> {
                            if(buff[2] == 0x78.toByte()){
                                mLastString = ""
                                //just a wait message, return OK
                                return UDSReturn.OK
                            }
                        }

                        else -> {
                            mLastString = buff.toHex()
                            return UDSReturn.ERROR_UNKNOWN
                        }
                    }
                }
                FLASH_ECU_CAL_SUBTASK.CHECKSUM_BLOCK -> {
                    when(checkResponse(buff)){

                        UDS_RESPONSE.POSITIVE_RESPONSE -> {
                            mCommand = UDS_COMMAND.START_ROUTINE.bytes + byteArrayOf(0x02.toByte(),0x02.toByte(),0x01.toByte(),0x05.toByte(), 0x00.toByte(), 0x04.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte())
                            mLastString = "Checksumming flashed block"
                            return UDSReturn.COMMAND_QUEUED
                        }
                        UDS_RESPONSE.ROUTINE_ACCEPTED -> {
                            mLastString = "Block Checksummed OK"
                            mCommand = UDS_COMMAND.TESTER_PRESENT.bytes
                            mTask = mTask.next()
                            return UDSReturn.COMMAND_QUEUED
                        }
                        UDS_RESPONSE.NEGATIVE_RESPONSE -> {
                            if(buff[2] == 0x78.toByte()){
                                mLastString = ""
                                //just a wait message, return OK
                                return UDSReturn.OK
                            }
                        }
                        else -> {
                            return UDSReturn.ERROR_UNKNOWN
                        }
                    }

                }
                FLASH_ECU_CAL_SUBTASK.VERIFY_PROGRAMMING_DEPENDENCIES -> {
                    //Verify programming dependencies, routine 0xFF01
                    when(checkResponse(buff)){
                        UDS_RESPONSE.POSITIVE_RESPONSE -> {
                            mCommand = UDS_COMMAND.START_ROUTINE.bytes + byteArrayOf(0xFF.toByte(), 0x01.toByte())
                            mLastString = "Verifying Programming Dependencies"
                            return UDSReturn.COMMAND_QUEUED
                        }

                        UDS_RESPONSE.ROUTINE_ACCEPTED -> {
                            mCommand = UDS_COMMAND.TESTER_PRESENT.bytes
                            mTask = mTask.next()
                            return UDSReturn.COMMAND_QUEUED
                        }
                        UDS_RESPONSE.NEGATIVE_RESPONSE -> {
                            if(buff[2] == 0x78.toByte()){
                                mLastString = ""
                                //just a wait message, return OK
                                return UDSReturn.OK
                            }
                        }

                        else -> {
                            return UDSReturn.ERROR_UNKNOWN
                        }

                    }

                }
                FLASH_ECU_CAL_SUBTASK.RESET_ECU -> {
                    DebugLog.d(TAG,"Response during reset ecu request: " + buff.toHex())
                    when(checkResponse(buff)){
                        UDS_RESPONSE.POSITIVE_RESPONSE -> {
                            mCommand = UDS_COMMAND.RESET_ECU.bytes
                            mLastString = "Resetting ECU!!!!"
                            return UDSReturn.COMMAND_QUEUED
                        }
                        UDS_RESPONSE.ECU_RESET_ACCEPTED -> {
                            mLastString = "Resetting ECU Complete, Please cycle Key"
                            bin = byteArrayOf()
                            mTask = FLASH_ECU_CAL_SUBTASK.NONE
                            return UDSReturn.FLASH_COMPLETE
                        }
                        UDS_RESPONSE.NEGATIVE_RESPONSE -> {
                            if (buff[2] == 0x78.toByte()) {
                                mLastString = ""
                                //just a wait message, return OK
                                return UDSReturn.OK
                            }
                            else {
                                return UDSReturn.ERROR_UNKNOWN
                            }
                        }
                        else -> {
                            return UDSReturn.ERROR_UNKNOWN
                        }
                    }
                }

                else -> {
                    return UDSReturn.ERROR_UNKNOWN
                }
            }
        }

        DebugLog.d(TAG, "Flash subroutine: " + mTask)
        return UDSReturn.ERROR_NULL
    }





    private fun checkResponse(input: ByteArray): UDS_RESPONSE{
        return UDS_RESPONSE.values().find {it.udsByte == input[0]} ?: UDS_RESPONSE.NO_RESPONSE
    }

}
