#pragma once
#include <stdio.h>
#include "FixedSizeQueue.h"
#include "MemoryMap.h"

#define  MAX_BUFFER_SIZE 250
#define  MAX_SEND_BUFFER_SIZE 20              // Increased from 15 - PM_SETBAUD is 17 bytes
#define  MAX_SEND_QUEUE_DEPTH 15
#define  PACKET_TIMEOUT_DEFINED 2000
#define  MAX_ZONE_COUNT 64                    // MERGED: raised from 31. PowerMaster30 has 64 zones.
#define  SLOW_PANEL_INIT_WAIT_TIME 10         // 10s is enough for even old panels to complete INIT

// Default download codes (factory defaults).
// PowerMax panels use 5650; PowerMaster panels use AAAA.
#define DL_CODE_POWERMAX   0x5650
#define DL_CODE_POWERMASTER 0xAAAA            // MERGED: PowerMaster factory default

// Download-code ladder.  We cannot know the panel type until the panel replies to
// DL_START with a 0x3C, and DL_START is exactly the message that needs the right
// code - so we cannot pick the code by panel type.  Instead we try the known
// factory defaults in turn whenever the panel answers DL_START with Access Denied.
// Mirrors the CFG.DLCODE_1/2/3 cycling in the HA integration (setNextDownloadCode).
#define DL_CODE_LADDER_LEN 4
#define DL_CODE_MAX_PASSES 2                  // stop after this many full passes of the ladder

// Sentinel for "no user override configured, use the ladder".
#define DL_CODE_NO_OVERRIDE (-1)

// Sentinel for "no user PIN configured, use whatever the panel gave us".
#define USER_PIN_NO_OVERRIDE (-1)

// Powerlink enrolment.  Panels that support auto-enrolment do NOT necessarily ask
// us to enrol - most of the time we have to ask them, by sending AB 0A once the
// EPROM download has completed.  If the panel accepts, it starts sending us its
// own AB 03 keep-alives, which is what "in Powerlink" actually means.
#define MAX_ENROL_ATTEMPTS   4
#define ENROL_RETRY_SECONDS  30

class PowerMaxAlarm;

enum PmaxCommand
{
    Pmax_ACK,
    Pmax_PING,
    Pmax_GETEVENTLOG,
    Pmax_DISARM,
    Pmax_ARMHOME,
    Pmax_ARMAWAY,
    Pmax_ARMHOME_INSTANT,
    Pmax_ARMAWAY_INSTANT,
    Pmax_ALARM,
    Pmax_ENABLE_BYPASS,
    Pmax_DISABLE_BYPASS,
    Pmax_REQSTATUS,
    Pmax_ENROLLREPLY,
    Pmax_INIT,
    Pmax_RESTORE,
    Pmax_DL_START,
    Pmax_DL_GET,
    Pmax_DL_EXIT,
    Pmax_DL_PANELFW,
    Pmax_DL_SERIAL,
    Pmax_DL_ZONESTR,
    Pmax_DL_ZONESIGNAL,
    Pmax_PM_KEEPALIVE,                        // MERGED: PowerMaster B0 keep-alive (B0 01 6A 00 43)
    Pmax_PM_SETBAUD,                          // MERGED: PowerMaster baud-rate negotiation
};

enum ZoneEvent
{
    ZE_None,
    ZE_TamperAlarm,
    ZE_TamperRestore,
    ZE_Open,
    ZE_Closed,
    ZE_Violated,
    ZE_PanicAlarm,
    ZE_RFJamming,
    ZE_TamperOpen,
    ZE_CommunicationFailure,
    ZE_LineFailure,
    ZE_Fuse,
    ZE_NotActive,
    ZE_LowBattery,
    ZE_ACFailure,
    ZE_FireAlarm,
    ZE_Emergency,
    ZE_SirenTamper,
    ZE_SirenTamperRestore,
    ZE_SirenLowBattery,
    ZE_SirenACFail
};

enum SystemStatus
{
    SS_Disarm        = 0x00,
    SS_Exit_Delay    = 0x01,
    SS_Exit_Delay2   = 0x02,
    SS_Entry_Delay   = 0x03,
    SS_Armed_Home    = 0x04,
    SS_Armed_Away    = 0x05,
    SS_User_Test     = 0x06,
    SS_Downloading   = 0x07,
    SS_Programming   = 0x08,
    SS_Installer     = 0x09,
    SS_Home_Bypass   = 0x0A,
    SS_Away_Bypass   = 0x0B,
    SS_Ready         = 0x0C,
    SS_Not_Ready     = 0x0D
};

enum PmAckType
{
    ACK_1,
    ACK_2
};

// Abstract output class for DumpToJson API
class IOutput
{
public:
    virtual void write(const char* str) = 0;
    void writeQuotedStr(const char* str);
    void writeJsonTag(const char* name, bool value, bool addComma = true);
    void writeJsonTag(const char* name, int value, bool addComma = true);
    void writeJsonTag(const char* name, const char* value, bool addComma = true, bool quoteValue = true);
};

class ConsoleOutput : public IOutput
{
public:
    void write(const char* str);
};

struct PlinkCommand {
    unsigned char buffer[MAX_SEND_BUFFER_SIZE];
    int size;
    const char* description;
    void (PowerMaxAlarm::*action)(const struct PlinkBuffer *);
};

struct PlinkBuffer {
    unsigned char buffer[MAX_BUFFER_SIZE];
    int size;
};

struct ZoneState {
    bool lowBattery;
    bool tamper;
    bool doorOpen;
    bool bypased;
    bool active;
};

struct Zone {
    bool enrolled;
    char name[20];
    unsigned char zoneType;
    const char* zoneTypeStr;
    unsigned char sensorId;
    const char* sensorType;
    const char* sensorMake;
    unsigned char signalStrength;    // 0=bad 1=poor 2=good 3=strong 0xFF=unknown
    ZoneState stat;
    ZoneEvent lastEvent;
    unsigned long lastEventTime;
    void DumpToJson(IOutput* outputStream);
};

struct PmQueueItem
{
    unsigned char buffer[MAX_SEND_BUFFER_SIZE];
    int bufferLen;
    const char* description;
    unsigned char expectedRepply;
    const char* options;
};

struct PmConfig
{
    bool parsedOK;
    // Codes parsed out of the EPROM block at byte 522 (page 0x02, index 0x0A).
    // Names follow the HA integration's EPROM map (pyeprom.py), which is the only
    // reliable reference for what these offsets actually are - the previous names
    // here were shifted by one slot and mislabelled the master code as the installer
    // code, which is actively misleading when debugging an Access Denied.
    char masterCode[5];             // byte 522  EPROM.MASTERCODE
    char installerCode[5];          // byte 524  EPROM.INSTALLERCODE
    char masterDownloadCode[5];     // byte 526  EPROM.MASTERDLCODE
    char installerDownloadCode[5];  // byte 528  EPROM.INSTALDLCODE
    char powerlinkCode[5];          // byte 530  the code a powerlink module registered
    char userPins[48][5];
    char phone[4][15];
    char serialNumber[15];
    char eprom[17];
    char software[17];
    unsigned char partitionCnt;
    unsigned char maxZoneCnt;
    unsigned char maxCustomCnt;
    unsigned char maxUserCnt;
    unsigned char maxPartitionCnt;
    unsigned char maxSirenCnt;
    unsigned char maxKeypad1Cnt;
    unsigned char maxKeypad2Cnt;
    unsigned char maxKeyfobCnt;

    PmConfig() { Init(); }

    void Init() { memset(this, 0, sizeof(PmConfig)); }
    void DumpToJson(IOutput* outputStream);
    int GetMasterPinAsHex() const;
};

// NOTE: If you want to add functionality, derive from this class and override virtual functions.
// This class should only contain code to communicate with PowerMax/PowerMaster alarms.
class PowerMaxAlarm
{
protected:
    unsigned char flags;
    SystemStatus stat;
    unsigned char alarmState;
    unsigned char alarmTrippedZones[MAX_ZONE_COUNT];
    Zone zone[MAX_ZONE_COUNT];
    PmConfig m_cfg;
    time_t lastIoTime;
    int m_iInitWaitTime;
    FixedSizeQueue<PmQueueItem, MAX_SEND_QUEUE_DEPTH> m_sendQueue;
    bool m_bEnrolCompleted;
    bool m_bDownloadMode;
    int m_iPanelType;
    int m_iModelType;
    bool m_bPowerMaster;
    PmAckType m_ackTypeForLastMsg;
    MemoryMap m_mapMain;
    MemoryMap m_mapExtended;
    PlinkCommand m_lastSentCommand;
    unsigned long m_ulLastPing;
    unsigned long m_ulNextPingDeadline;
    // MERGED: tracks whether we have sent the PowerMaster zone-init B0 request
    // (subtypes 0x1D/0x1F/0x21/0x2D) since the last init() or re-enrolment.
    // Reset to false so the first B0 0x39 exchange after any (re)connection
    // piggybacks the zone-data request.
    bool m_bPmZoneDataRequested;

    // Download-code ladder state.  -1 means "currently using the user override";
    // 0..DL_CODE_LADDER_LEN-1 is a position in the built-in ladder.
    // Initialised here as well as in resetDownloadCode(), because this class has no
    // constructor and getDownloadCode() may be reached before init() runs.
    int  m_iDlCodeIndex     = 0;
    // Number of times we have moved on to the next download code since init().
    int  m_iDlCodeAdvances  = 0;
    // Refusals seen for the current download code. The first DL_START of a connection
    // is often refused regardless of the code, so we retry once before advancing.
    int  m_iDlCodeRetry     = 0;

    // Panel capabilities, learned from the panel type in the 0x3C message.
    //   m_bAbSupported : panel understands 0xAB messages (ping / restore / enrol).
    //                    Only the PowerMaster 360 and 360R do not.
    //   m_bAutoEnrol   : panel can be enrolled by us sending AB 0A, rather than
    //                    requiring the installer to do it from the panel keypad.
    bool m_bAbSupported     = true;
    bool m_bAutoEnrol       = false;
    // Whether this panel type supports the INIT command. Only known once the 0x3C
    // panel-info reply has arrived, which is why init() never sends INIT.
    bool m_bInitSupported   = false;

    // Powerlink enrolment state.
    // m_bPowerlinkAlive goes true when the panel sends us its first AB 03 keep-alive,
    // which is the only real confirmation that enrolment succeeded.
    // Which partitions arm/disarm (0xA1) commands apply to, as a bitmask
    // (bit0 = partition 1).  0x07 = all three, which is correct for panels that do
    // not use partitions.  When the panel DOES use partitions, processSettings()
    // narrows this to the partitions that actually have zones assigned - asking a
    // panel to act on a partition that does not exist can get the whole command
    // refused with Access Denied.
    unsigned char m_partitionMask = 0x07;

    // True when the panel actually uses partitions (EPROM PART_ENABLED).
    // Partitioned panels send a different, untrustworthy A7 layout - see OnStatusChange.
    bool m_bPartitionsEnabled = false;

    bool m_bPowerlinkAlive  = false;
    int  m_iEnrolAttempts   = 0;
    unsigned long m_ulNextEnrolAttempt = 0;   // 0 = not enrolling / gave up

    void tryEnrolPowerlink();

public:
    // ---------------------------------------------------------------------
    // User PIN override.  Used by addPin() for arm / disarm / bypass / event-log
    // (0xA1, 0xAA, 0xA0).
    //
    // When set to a real value it ALWAYS wins, because it is the only way to drive
    // a panel whose stored user code we cannot read (Listen-only mode) or which
    // rejects the code we did read.  Leave it as USER_PIN_NO_OVERRIDE to use the
    // code read out of the panel's EPROM instead, which is the normal case.
    //
    // This is NOT the download or enrolment code; see Powerlink_DownloadCode_Override.
    // ---------------------------------------------------------------------
    long Powerlink_User_PIN_Code   = USER_PIN_NO_OVERRIDE;

    // ---------------------------------------------------------------------
    // Download code.  Used by DL_START (0x24) and ENROLLREPLY (0xAB 0A).
    // Set to a value in 0x0000-0xFFFF to force one specific code, or leave as
    // DL_CODE_NO_OVERRIDE to work through the built-in ladder instead.
    // ---------------------------------------------------------------------
    long Powerlink_DownloadCode_Override = DL_CODE_NO_OVERRIDE;

    bool Powerlink_ListenNotEnrol  = false;
    bool Powerlink_SlowComms       = false;
    unsigned char Powermax_Bypass_Zones[4] = {0};

    // Current download code being tried (override, or the current ladder entry).
    int  getDownloadCode() const;

    void init(int initWaitTime = SLOW_PANEL_INIT_WAIT_TIME);
    void sendNextCommand();
    bool restoreCommsIfLost();
    void clearQueue() { m_sendQueue.clear(); }
    bool sendCommand(PmaxCommand cmd);
    void handlePacket(PlinkBuffer* commandBuffer);
    bool setDateTime(unsigned char year, unsigned char month, unsigned char day,
                     unsigned char hour, unsigned char minutes, unsigned char seconds);
    static bool isBufferOK(const PlinkBuffer* commandBuffer);
    const char* getZoneName(unsigned char zoneId);
    bool isConfigParsed() const { return m_cfg.parsedOK; }
    unsigned int getEnrolledZoneCnt() const;
    unsigned long getSecondsFromLastComm() const;
    void dumpToJson(IOutput* outputStream);

#ifdef _MSC_VER
    void IZIZTODO_testMap();
#endif

    // -----------------------------------------------------------------------
    // Raw message handlers – override in derived class for notifications
    // -----------------------------------------------------------------------
    virtual void OnStatusUpdateZoneBat(const PlinkBuffer* Buff);
    virtual void OnStatusUpdatePanel(const PlinkBuffer* Buff);
    virtual void OnStatusUpdateZoneBypassed(const PlinkBuffer* Buff);
    virtual void OnStatusUpdateZoneTamper(const PlinkBuffer* Buff);
    virtual void OnStatusChange(const PlinkBuffer* Buff);
    virtual void OnStatusUpdate(const PlinkBuffer* Buff);
    virtual void OnEventLog(const PlinkBuffer* Buff);
    virtual void OnAccessDenied(const PlinkBuffer* Buff);
    virtual void OnAck(const PlinkBuffer* Buff);
    virtual void OnTimeOut(const PlinkBuffer* Buff);
    virtual void OnStop(const PlinkBuffer* Buff);
    virtual void OnEnroll(const PlinkBuffer* Buff);
    virtual void OnPing(const PlinkBuffer* Buff);
    virtual void OnPanelInfo(const PlinkBuffer* Buff);
    virtual void OnDownloadInfo(const PlinkBuffer* Buff);
    virtual void OnDownloadSettings(const PlinkBuffer* Buff);

    // MERGED: PowerMaster B0 message handler
    // Called for every 0xB0 packet.  Override to handle additional subtypes.
    // The default implementation handles the most common ones (0x24, 0x0F, 0x18, 0x19, 0x1D, 0x6A).
    virtual void OnPowerMasterMessage(const PlinkBuffer* Buff);

    // -----------------------------------------------------------------------
    // Higher-level event callbacks – override for common tasks
    // -----------------------------------------------------------------------
    // armType: 0x51=Arm Home, 0x53=Quick Arm Home, 0x52=Arm Away, 0x54=Quick Arm Away
    virtual void OnSytemArmed(unsigned char armType, const char* armTypeStr,
                               unsigned char whoArmed, const char* whoArmedStr) {};

    virtual void OnSytemDisarmed(unsigned char whoDisarmed, const char* whoDisarmedStr) {};

    virtual void OnAlarmStarted(unsigned char alarmType, const char* alarmTypeStr,
                                 unsigned char zoneTripped, const char* zoneTrippedStr) {};

    virtual void OnAlarmCancelled(unsigned char whoDisarmed, const char* whoDisarmedStr) {};

    virtual void OnPanelDateTime(unsigned char year, unsigned char month, unsigned char day,
                                  unsigned char hour, unsigned char minutes, unsigned char seconds) {};

    // MERGED: ACK-and-ignore handler for packets that are received but not explicitly handled.
    // Used as a catch-all for A5 subtypes the PowerMaster sends unsolicited (e.g. 0x01, 0x05).
    // Must be public so the file-scope dispatch table can take its address.
    virtual void OnUnhandledMessage(const PlinkBuffer* Buff);

    // MERGED: Called when PowerMaster requests a baud-rate change.
    // Override to switch the physical serial port baud rate (e.g. on ESP8266).
    virtual void OnBaudRateChange(unsigned int newBaudRate) {};

    virtual void OnDumpToJsonStarted(IOutput* outputStream) {};

    // -----------------------------------------------------------------------
    // String helpers – override to provide custom text
    // -----------------------------------------------------------------------
    virtual const char* GetStrPmaxSystemStatus(int index);
    virtual const char* GetStrSystemStateFlags(int index);
    virtual const char* GetStrPmaxZoneEventTypes(int index);
    virtual const char* GetStrPmaxLogEvents(int index);
    virtual const char* GetStrPmaxPanelType(int index);
    virtual const char* GetStrPmaxZoneTypes(int index);
    virtual const char* GetStrPmaxEventSource(int index);

    SystemStatus GetSystemStatus() const { return stat; }

protected:
    void addPin(unsigned char* bufferToSend, int pos = 4, bool useMasterCode = false);
    bool isFlagSet(unsigned char id) const { return (flags & 1<<id) != 0; }
    bool isAlarmEvent() const { return isFlagSet(7); }
    bool isZoneEvent()  const { return isFlagSet(5); }
    void format_SystemStatus(char* tpbuff, int buffSize);
    bool queueCommand(const unsigned char* buffer, int bufferLen, const char* description,
                      unsigned char expectedRepply = 0x00, const char* options = NULL);
    void powerLinkEnrolled();
    void processSettings();
    int  readMemoryMap(const unsigned char* msg, unsigned char* buffOut, int buffOutSize);
    void writeMemoryMap(int iPage, int iIndex, const unsigned char* sData, int sDataLen);
    bool sendBuffer(const unsigned char* data, int bufferSize);
    void sendBuffer(struct PlinkBuffer* Buff);
    static PmAckType calculateAckType(const unsigned char* deformattedBuffer, int bufferLen);
    void startKeepAliveTimer();
    void stopKeepAliveTimer();

    // MERGED: queue a B0 data-request packet (subtypes[] = array of B0 subtype bytes, count = length)
    void sendPmB0Request(const unsigned char* subtypes, int count);

    // Move on to the next download code in the ladder.  Returns false when we have
    // exhausted DL_CODE_MAX_PASSES full passes and should stop retrying.
    bool advanceDownloadCode();
    void resetDownloadCode();
};

/* OS abstraction layer – provide your own implementation for non-Windows targets */
#ifndef LOG_INFO
#define LOG_EMERG   0
#define LOG_ALERT   1
#define LOG_CRIT    2
#define LOG_ERR     3
#define LOG_WARNING 4
#define LOG_NOTICE  5
#define LOG_INFO    6
#define LOG_DEBUG   7
#endif
#define LOG_NO_FILTER 0
// On the ESP8266 a plain string literal is placed in DRAM, so every debug format
// string costs RAM permanently whether or not it is ever printed. There are ~95 of
// them in this library alone, which is several KB of heap given away for nothing.
// PSTR() moves them into flash instead.
//
// The matching os_debugLog() implementation MUST then read the format with
// vsnprintf_P() rather than vsnprintf(), because the pointer no longer points at
// normal memory. Non-Arduino builds keep plain literals and plain vsnprintf().
#ifdef ARDUINO
  #include <pgmspace.h>
  #define PM_LOG_FMT(f) PSTR(f)
#else
  #define PM_LOG_FMT(f) (f)
#endif

#define DEBUG(pri, fmt, ...)     os_debugLog(pri, false, __FUNCTION__, __LINE__, PM_LOG_FMT(fmt), ##__VA_ARGS__);
#define DEBUG_RAW(pri, fmt, ...) os_debugLog(pri, true,  __FUNCTION__, __LINE__, PM_LOG_FMT(fmt), ##__VA_ARGS__);
int log_console_setlogmask(int mask);

bool os_pmComPortInit(const char* portName);
int  os_pmComPortRead(void* writePos, int bytesToRead);
int  os_pmComPortWrite(const void* dataToWrite, int bytesToWrite);
bool os_pmComPortClose();
void os_usleep(int microseconds);
int  os_cfg_getPacketTimeout();
void os_debugLog(int priority, bool raw, const char* function, int line, const char* format, ...);
void os_strncat_s(char* dst, int dst_size, const char* src);
bool os_getLocalTime(unsigned char& year, unsigned char& month, unsigned char& day,
                     unsigned char& hour, unsigned char& minutes, unsigned char& seconds);
unsigned long os_getCurrentTimeSec();
