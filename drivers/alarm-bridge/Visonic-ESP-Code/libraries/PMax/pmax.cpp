// pmax_merged.cpp
// Based on pmax.cpp (cjcharles0/Hubitat) with PowerMaster fixes merged from
// davesmeghead/visonic (Home Assistant integration).
// See DIFF_ANALYSIS.md for details of every change.

#include "pmax.h"
#include <string.h>
#include <stdlib.h>
#include <cctype>
#include <limits.h>

#define IMPEMENT_GET_FUNCTION(tblName)\
const char* PowerMaxAlarm::GetStr##tblName(int index)\
{\
    int nameCnt = sizeof(tblName)/sizeof(tblName[0]);\
    if(index < nameCnt)\
    {\
        return tblName[index];\
    }\
\
    return "??";\
}

/*
'########################################################
' PowerMax/Master send messages
'########################################################

' ### ACK messages ###
Private VMSG_ACK1 {&H02]              'NONE
Private VMSG_ACK2 {&H02, &H43]       'NONE

' ### Arm/Disarm/Status ###
Private VMSG_ARMDISARM {&HA1, &H00, &H00, &H00, &H00, &H00, &H07, &H00, &H00, &H00, &H00, &H43] 'MERGED: byte[6]=0x07 arms all 3 partitions
Private VMSG_STATUS    {&HA2, &H00, &H00, &H3F, &H00, &H00, &H00, &H00, &H00, &H00, &H00, &H43] 'MERGED: 0x3F requests all A5 sub-types

' #### PowerMaster messages ###
Private VMSG_PM_KEEPALIVE {&HB0, &H01, &H6A, &H00, &H43]                                         'MERGED: PM keep-alive (replaces AB 03 ping)
'Private VMSG_PM_SETBAUD   {&HB0, &H00, &H41, &H0D, &HAA, &HAA, ...}                              'MERGED: PM baud-rate negotiation (38400)
*/

// ############################################################
// PowerMax/Master download message definitions
// ############################################################
// Pos.  0  1  2  3  4  5  6  7  8  9  A
// E.g. 3E 00 04 20 00 B0 00 00 00 00 00
// 1=Index, 2=Page, 3=Low Length, 4=High Length, 5=Always B0?
// PowerMaster30 extended format (FF/FF pages):
// Pos.  0  1  2  3  4  5  6  7  8  9  A
// E.g. 3E FF FF 42 1F B0 05 48 01 00 00
// 1=FF 2=FF 3=Low Length, 4=High Length, 5=Always B0, 6=Index, 7=Page
#define VMSG_DL_PANELFW         {0x3E, 0x00, 0x04, 0x20, 0x00, 0xB0, 0x00, 0x00, 0x00, 0x00, 0x00}
#define VMSG_DL_ZONESTR         {0x3E, 0x00, 0x19, 0x00, 0x02, 0xB0, 0x00, 0x00, 0x00, 0x00, 0x00}
#define VMSG_DL_SERIAL          {0x3E, 0x30, 0x04, 0x08, 0x00, 0xB0, 0x00, 0x00, 0x00, 0x00, 0x00}
#define VMSG_DL_TIME            {0x3E, 0xF8, 0x00, 0x20, 0x00, 0xB0, 0x00, 0x00, 0x00, 0x00, 0x00}
#define VMSG_DL_COMMDEF         {0x3E, 0x01, 0x01, 0x1E, 0x00, 0xB0, 0x00, 0x00, 0x00, 0x00, 0x00}
#define VMSG_DL_USERPINCODES    {0x3E, 0xFA, 0x01, 0x10, 0x00, 0xB0, 0x00, 0x00, 0x00, 0x00, 0x00}
#define VMSG_DL_OTHERPINCODES   {0x3E, 0x0A, 0x02, 0x0A, 0x00, 0xB0, 0x00, 0x00, 0x00, 0x00, 0x00}
#define VMSG_DL_PHONENRS        {0x3E, 0x36, 0x01, 0x20, 0x00, 0xB0, 0x00, 0x00, 0x00, 0x00, 0x00}
#define VMSG_DL_PARTITIONS      {0x3E, 0x00, 0x03, 0xF0, 0x00, 0xB0, 0x00, 0x00, 0x00, 0x00, 0x00}
#define VMSG_DL_ZONES           {0x3E, 0x00, 0x09, 0x78, 0x00, 0xB0, 0x00, 0x00, 0x00, 0x00, 0x00}
#define VMSG_DL_ZONENAMES       {0x3E, 0x40, 0x0B, 0x1E, 0x00, 0xB0, 0x00, 0x00, 0x00, 0x00, 0x00}
#define VMSG_DL_ZONESIGNAL      {0x3E, 0xDA, 0x09, 0x1C, 0x00, 0xB0, 0x03, 0x00, 0x03, 0x00, 0x03}

// PowerMaster-specific EPROM download addresses
#define VMSG_DL_MASTER_USERPINCODES  {0x3E, 0x98, 0x0A, 0x60, 0x00, 0xB0, 0x00, 0x00, 0x00, 0x00, 0x00}
#define VMSG_DL_MASTER_ZONENAMES     {0x3E, 0x60, 0x09, 0x40, 0x00, 0xB0, 0x00, 0x00, 0x00, 0x00, 0x00}
#define VMSG_DL_MASTER_ZONES         {0x3E, 0x72, 0xB8, 0x80, 0x02, 0xB0, 0x00, 0x00, 0x00, 0x00, 0x00}

// ############################################################
// Panel configuration tables
// Indexed by panel type (0–17):
//   0=PowerMax,          1=PowerMax+,         2=PowerMax Pro,
//   3=PowerMax Complete, 4=PowerMax Pro Part,  5=PowerMax Complete Part,
//   6=PowerMax Express,  7=PowerMaster10,      8=PowerMaster30,
//   9=unknown,          10=PowerMaster33,      11=unknown,
//  12=unknown,          13=PowerMaster360,     14=unknown,
//  15=PowerMaster33,    16=PowerMaster360R,    17=Default/fallback
// ############################################################
//                              0  1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17
#define VCFG_PARTITIONS   { 1, 1, 1, 1, 3, 3, 1, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 1 }
#define VCFG_KEYFOBS      { 8, 8, 8, 8, 8, 8, 8, 8,32,32,32,32,32,32,32,32,32, 8 }
#define VCFG_1WKEYPADS    { 8, 8, 8, 8, 8, 8, 8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 8 }
#define VCFG_2WKEYPADS    { 2, 2, 2, 2, 2, 2, 2, 8,32,32,32,32,32,32,32,32,32, 2 }
#define VCFG_SIRENS       { 2, 2, 2, 2, 2, 2, 2, 4, 8, 8, 8, 8, 8, 8, 8, 8, 8, 2 }
#define VCFG_USERCODES    { 8, 8, 8, 8, 8, 8, 8, 8,48,48,48,48,48,48,48,48,48, 8 }
#define VCFG_WIRELESS     {28,28,28,28,28,28,29,29,62,62,62,62,62,64,62,62,64,28 }
#define VCFG_WIRED        { 2, 2, 2, 2, 2, 2, 1, 1, 2, 2, 2, 2, 2, 0, 2, 2, 0, 2 }
#define VCFG_ZONECUSTOM   { 0, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5 }
// Does the panel understand 0xAB messages (ping / restore / enrol)?
// Mirrors CFG.AB_SUPPORTED in the HA integration. Only the 360 (13) and 360R (16)
// do not - those are the only panels that should get the B0 01 6A keep-alive.
#define VCFG_AB_SUPPORTED { 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 1, 1, 0, 0 }
// Can we enrol ourselves by sending AB 0A, or must the installer do it at the panel?
// Mirrors CFG.AUTO_ENROL. PowerMax and PowerMax+ / Pro (1,2) need manual enrolment.
#define VCFG_AUTO_ENROL   { 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 1, 1, 0, 0 }
// Does the panel support the INIT command (AB 0A 00 01)?  Mirrors CFG.INIT_SUPPORT.
// Only meaningful once the panel type is known from the 0x3C, which is why INIT is
// never sent on a first connection - see init().
#define VCFG_INIT_SUPPORT { 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 1, 1, 0, 0 }

// ############################################################
// setDateTime helper
// ############################################################
bool PowerMaxAlarm::setDateTime(unsigned char year, unsigned char month, unsigned char day,
                                 unsigned char hour, unsigned char minutes, unsigned char seconds)
{
    unsigned char buff[] = {0x46,0xF8,0x00,seconds,minutes,hour,day,month,year,0xFF,0xFF};
    return queueCommand(buff, sizeof(buff), "SET_DATE_TIME", 0xA0);
}

// ############################################################
// sendCommand
// ############################################################
bool PowerMaxAlarm::sendCommand(PmaxCommand cmd)
{
    switch(cmd)
    {
    case Pmax_ACK:
        {
            if(m_ackTypeForLastMsg == ACK_1)
            {
                unsigned char buff[] = {0x02};
                return sendBuffer(buff, sizeof(buff));
            }
            else
            {
                unsigned char buff[] = {0x02,0x43};
                return sendBuffer(buff, sizeof(buff));
            }
        }

    case Pmax_PING:
        {
            if(m_bDownloadMode == true)
            {
                DEBUG(LOG_WARNING,"Sending Ping in Download Mode?");
            }
            unsigned char buff[] = {0xAB,0x03,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x43};
            return sendBuffer(buff, sizeof(buff));
        }

    // MERGED: PowerMaster keep-alive uses B0 01 6A 00 43 instead of the AB 03 ping
    case Pmax_PM_KEEPALIVE:
        {
            unsigned char buff[] = {0xB0,0x01,0x6A,0x00,0x43};
            return sendBuffer(buff, sizeof(buff));
        }

    // MERGED: PowerMaster baud-rate negotiation (switches panel to 38400)
    // AA AA = placeholder for user PIN (filled by addPin), BB BB = new baud rate bytes (38400 = 0x9600 → 0x96 0x00)
    case Pmax_PM_SETBAUD:
        {
            // 0xAA 0xAA = user PIN placeholder; baud 38400 = 0x96 0x00
            unsigned char buff[] = {0xB0,0x00,0x41,0x0D,0xAA,0xAA,0x01,0xFF,0x28,0x0C,0x05,0x01,0x00,0x96,0x00,0x00,0x05,0x43};
            addPin(buff, 4, true);  // insert master PIN at bytes 4 & 5
            DEBUG(LOG_INFO,"Sending PM_SETBAUD (38400) to PowerMaster");
            // Notify the hardware layer to switch baud rate after the panel ACKs
            OnBaudRateChange(38400);
            return queueCommand(buff, sizeof(buff), "Pmax_PM_SETBAUD");
        }

    case Pmax_GETEVENTLOG:
        {
            unsigned char buff[] = {0xA0,0x00,0x00,0x00,0x12,0x34,0x00,0x00,0x00,0x00,0x00,0x43}; addPin(buff, 4, true);
            return queueCommand(buff, sizeof(buff), "Pmax_GETEVENTLOG", 0xA0, "PIN:MasterCode:4");
        }

    case Pmax_DISARM:
        {
            // MERGED: byte[6] = 0x07 to address all 3 partitions.
            // addPin() prefers the EPROM master code and falls back to the configured
            // user PIN, which is what PowerMaster panels always end up using because
            // they never download the EPROM.
            unsigned char buff[] = {0xA1,0x00,0x00,0x00,0x12,0x34,0x07,0x00,0x00,0x00,0x00,0x43}; addPin(buff, 4, true);
            buff[6] = m_partitionMask;   // partitions this command applies to
            return sendBuffer(buff, sizeof(buff));
        }

    case Pmax_ARMHOME:
        {
            unsigned char buff[] = {0xA1,0x00,0x00,0x04,0x12,0x34,0x07,0x00,0x00,0x00,0x00,0x43}; addPin(buff, 4, true);
            buff[6] = m_partitionMask;   // partitions this command applies to
            return sendBuffer(buff, sizeof(buff));
        }

    case Pmax_ARMAWAY:
        {
            unsigned char buff[] = {0xA1,0x00,0x00,0x05,0x12,0x34,0x07,0x00,0x00,0x00,0x00,0x43}; addPin(buff, 4, true);
            buff[6] = m_partitionMask;   // partitions this command applies to
            return sendBuffer(buff, sizeof(buff));
        }

    case Pmax_ARMHOME_INSTANT:
        {
            unsigned char buff[] = {0xA1,0x00,0x00,0x14,0x12,0x34,0x07,0x00,0x00,0x00,0x00,0x43}; addPin(buff, 4, true);
            buff[6] = m_partitionMask;   // partitions this command applies to
            return sendBuffer(buff, sizeof(buff));
        }

    case Pmax_ARMAWAY_INSTANT:
        {
            unsigned char buff[] = {0xA1,0x00,0x00,0x15,0x12,0x34,0x07,0x00,0x00,0x00,0x00,0x43}; addPin(buff, 4, true);
            buff[6] = m_partitionMask;   // partitions this command applies to
            return sendBuffer(buff, sizeof(buff));
        }

    case Pmax_ALARM:
        {
            unsigned char buff[] = {0xA1,0x00,0x00,0x07,0x12,0x34,0x07,0x00,0x00,0x00,0x00,0x43}; addPin(buff, 4, true);
            buff[6] = m_partitionMask;   // partitions this command applies to
            return sendBuffer(buff, sizeof(buff));
        }

    case Pmax_ENABLE_BYPASS:
        {
            unsigned char buff[] = {0xAA,0x12,0x34,
                Powermax_Bypass_Zones[0],Powermax_Bypass_Zones[1],
                Powermax_Bypass_Zones[2],Powermax_Bypass_Zones[3],
                0x00,0x00,0x00,0x00,0x43}; addPin(buff, 1, true);
            return sendBuffer(buff, sizeof(buff));
        }

    case Pmax_DISABLE_BYPASS:
        {
            unsigned char buff[] = {0xAA,0x12,0x34,0x00,0x00,0x00,0x00,
                Powermax_Bypass_Zones[0],Powermax_Bypass_Zones[1],
                Powermax_Bypass_Zones[2],Powermax_Bypass_Zones[3],0x43}; addPin(buff, 1, true);
            return sendBuffer(buff, sizeof(buff));
        }

    case Pmax_REQSTATUS:
        {
            // MERGED: byte[3] changed from 0x00 to 0x3F.
            // 0x3F = bitmask requesting A5 subtypes 01-06 (zone open, battery, tamper,
            //        panel state, zone events, bypass/enrol). Without this the panel
            //        sends no A5 data in response.
            unsigned char buff[] = {0xA2,0x00,0x00,0x3F,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x43};
            return sendBuffer(buff, sizeof(buff));
        }

    case Pmax_ENROLLREPLY:
        {
            m_bDownloadMode = false;
            // The enrolment code is the *download* code, not the user PIN.
            // (HA: Send.ENROL inserts self.DownloadCode at offset 4.)
            unsigned char buff[] = {0xAB,0x0A,0x00,0x00,0x12,0x34,0x00,0x00,0x00,0x00,0x00,0x43};
            const int dl = getDownloadCode();
            buff[4] = (dl >> 8) & 0xFF;
            buff[5] = dl & 0xFF;
            return queueCommand(buff, sizeof(buff), "Pmax_ENROLLREPLY");
        }

    case Pmax_RESTORE:
        {
            if (Powerlink_ListenNotEnrol) return true;
            unsigned char buff[] = {0xAB,0x06,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x43};
            return queueCommand(buff, sizeof(buff), "Pmax_RESTORE");
        }

    case Pmax_INIT:
        {
            if (Powerlink_ListenNotEnrol) return true;
            unsigned char buff[] = {0xAB,0x0A,0x00,0x01,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x43};
            return queueCommand(buff, sizeof(buff), "Pmax_INIT");
        }

    case Pmax_DL_PANELFW:
        {
            unsigned char buff[] = {0x3E, 0x00, 0x04, 0x20, 0x00, 0xB0, 0x00, 0x00, 0x00, 0x00, 0x00};
            return queueCommand(buff, sizeof(buff), "Pmax_DL_PANELFW", 0x3F);
        }

    case Pmax_DL_SERIAL:
        {
            unsigned char buff[] = {0x3E, 0x30, 0x04, 0x08, 0x00, 0xB0, 0x00, 0x00, 0x00, 0x00, 0x00};
            return queueCommand(buff, sizeof(buff), "Pmax_DL_SERIAL", 0x3F);
        }

    case Pmax_DL_ZONESTR:
        {
            unsigned char buff[] = {0x3E, 0x00, 0x19, 0x00, 0x02, 0xB0, 0x00, 0x00, 0x00, 0x00, 0x00};
            return queueCommand(buff, sizeof(buff), "Pmax_DL_ZONESTR", 0x3F);
        }

    case Pmax_DL_ZONESIGNAL:
        {
            unsigned char buff[] = {0x3E, 0xDA, 0x09, 0x1C, 0x00, 0xB0, 0x03, 0x00, 0x03, 0x00, 0x03};
            return queueCommand(buff, sizeof(buff), "Pmax_DL_ZONESIGNAL", 0x3F);
        }

    case Pmax_DL_GET:
        {
            if (Powerlink_ListenNotEnrol) return true;
            unsigned char buff[] = {0x0A};
            return queueCommand(buff, sizeof(buff), "Pmax_DL_GET", 0x33);
        }

    case Pmax_DL_START:
        {
            if (Powerlink_ListenNotEnrol) return true;
            if(m_bDownloadMode == false)
                m_bDownloadMode = true;
            else
                DEBUG(LOG_WARNING,"Already in Download Mode?");
            stopKeepAliveTimer();
            // The download code cannot be chosen by panel type: m_bPowerMaster is only
            // known once the panel answers this very message with a 0x3C.  Instead use
            // the user override if one is set, else work through the ladder, advancing
            // each time the panel replies Access Denied (see OnAccessDenied).
            unsigned char buff[] = {0x24,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00};
            const int dl = getDownloadCode();
            buff[3] = (dl >> 8) & 0xFF;
            buff[4] = dl & 0xFF;
            DEBUG(LOG_INFO,"DL_START using download code %04X (%s)", dl,
                  (m_iDlCodeIndex < 0) ? "user override" : "ladder");
            return queueCommand(buff, sizeof(buff), "Pmax_DL_START", 0x3C, "PIN:DownloadCode:3");
        }

    case Pmax_DL_EXIT:
        {
            if (Powerlink_ListenNotEnrol) return true;
            unsigned char buff[] = {0x0F};
            if(m_bDownloadMode)
                m_bDownloadMode = false;
            else
                DEBUG(LOG_WARNING,"Not in Download Mode?");
            return queueCommand(buff, sizeof(buff), "Pmax_DL_EXIT");
        }

    default:
        return false;
    }
}

// ############################################################
// Dispatch table – maps received packets to handler functions
// ############################################################
// FF = wildcard (match any byte)
struct PlinkCommand PmaxCommandTable[] =
{
    {{0x08                                                        }, 1  ,"Access denied"              ,&PowerMaxAlarm::OnAccessDenied       },
    {{0x08,0x43                                                   }, 2  ,"Access denied 2"            ,&PowerMaxAlarm::OnAccessDenied       },
    {{0xA0,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0x43},12  ,"Event Log"                  ,&PowerMaxAlarm::OnEventLog           },
    {{0xA5,0xFF,0x02,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0x43},12  ,"Status Update Zone Battery" ,&PowerMaxAlarm::OnStatusUpdateZoneBat},
    {{0xA5,0xFF,0x03,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0x43},12  ,"Status Update Zone tamper"  ,&PowerMaxAlarm::OnStatusUpdateZoneTamper},
    {{0xA5,0xFF,0x04,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0x43},12  ,"Status Update Panel"        ,&PowerMaxAlarm::OnStatusUpdatePanel  },
    {{0xA5,0xFF,0x06,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0x43},12  ,"Status Update Zone Bypassed",&PowerMaxAlarm::OnStatusUpdateZoneBypassed},
    {{0xA7,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0x43},12  ,"Panel status change"        ,&PowerMaxAlarm::OnStatusChange       },
    {{0xAB,0x0A,0x00,0x01,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x43},12 ,"Enroll request"             ,&PowerMaxAlarm::OnEnroll             },
    {{0xAB,0x03,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0x43},12  ,"Ping"                       ,&PowerMaxAlarm::OnPing               },
    {{0x3C,0xFD,0x0A,0x00,0x00,0xFF,0xFF,0xFF,0xFF               },-9  ,"Panel Info"                 ,&PowerMaxAlarm::OnPanelInfo          },
    {{0x3F,0xFF,0xFF,0xFF                                         },-4  ,"Download Info"              ,&PowerMaxAlarm::OnDownloadInfo       },
    {{0x33,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF      },11  ,"Download Settings"          ,&PowerMaxAlarm::OnDownloadSettings   },
    {{0x02,0x43                                                   }, 2  ,"Acknowledgement"            ,&PowerMaxAlarm::OnAck                },
    {{0x02                                                        }, 1  ,"Acknowledgement 2"          ,&PowerMaxAlarm::OnAck                },
    {{0x06                                                        }, 1  ,"Time Out"                   ,&PowerMaxAlarm::OnTimeOut            },
    {{0x0B                                                        }, 1  ,"Stop (Dload Complete)"      ,&PowerMaxAlarm::OnStop               },
    // MERGED: PowerMaster sends B0 messages for all real-time zone/panel data.
    //         Match any B0 packet (minimum 4 bytes: B0 type subtype len).
    {{0xB0,0xFF,0xFF,0xFF                                         },-4  ,"PowerMaster B0"             ,&PowerMaxAlarm::OnPowerMasterMessage },
    // MERGED: Catch-all for any A5 subtype not explicitly handled above (e.g. 0x01, 0x05).
    //         PowerMaster sends these unsolicited after RESTORE. Just ACK them.
    {{0xA5,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF },-5  ,"A5 Status (unhandled)"      ,&PowerMaxAlarm::OnUnhandledMessage   },
};

// ############################################################
// String tables
// ############################################################
const char* PmaxSystemStatus[] = {
    "Disarmed","Exit Delay","Exit Delay","Entry Delay",
    "Armed Home","Armed Away","User Test","Downloading",
    "Programming","Installer","Home Bypass","Away Bypass",
    "Ready","Not Ready"
};

const char* SystemStateFlags[] = {
    "Ready","Alert-in-Memory","Trouble","Bypass-On",
    "Last-10-sec-delay","Zone-event","Arm/disarm-event","Alarm-event"
};

const char* PmaxLogEvents[] = {
    "None","Interior Alarm","Perimeter Alarm","Delay Alarm",
    "24h Silent Alarm","24h Audible Alarm","Tamper","Control Panel Tamper",
    "Tamper Alarm","Tamper Alarm","Communication Loss","Panic From Keyfob",
    "Panic From Control Panel","Duress","Confirm Alarm","General Trouble",
    "General Trouble Restore","Interior Restore","Perimeter Restore","Delay Restore",
    "24h Silent Restore","24h Audible Restore","Tamper Restore","Control Panel Tamper Restore",
    "Tamper Restore","Tamper Restore","Communication Restore","Cancel Alarm",
    "General Restore","Trouble Restore","Not used","Recent Close",
    "Fire","Fire Restore","No Active","Emergency",
    "No used","Disarm Latchkey","Panic Restore","Supervision (Inactive)",
    "Supervision Restore (Active)","Low Battery","Low Battery Restore","AC Fail",
    "AC Restore","Control Panel Low Battery","Control Panel Low Battery Restore","RF Jamming",
    "RF Jamming Restore","Communications Failure","Communications Restore","Telephone Line Failure",
    "Telephone Line Restore","Auto Test","Fuse Failure","Fuse Restore",
    "Keyfob Low Battery","Keyfob Low Battery Restore","Engineer Reset","Battery Disconnect",
    "1-Way Keypad Low Battery","1-Way Keypad Low Battery Restore","1-Way Keypad Inactive","1-Way Keypad Restore Active",
    "Low Battery","Clean Me","Fire Trouble","Low Battery",
    "Battery Restore","AC Fail","AC Restore","Supervision (Inactive)",
    "Supervision Restore (Active)","Gas Alert","Gas Alert Restore","Gas Trouble",
    "Gas Trouble Restore","Flood Alert","Flood Alert Restore","X-10 Trouble",
    "X-10 Trouble Restore","Arm Home","Arm Away","Quick Arm Home",
    "Quick Arm Away","Disarm","Fail To Auto-Arm","Enter To Test Mode",
    "Exit From Test Mode","Force Arm","Auto Arm","Instant Arm",
    "Bypass","Fail To Arm","Door Open","Communication Established By Control Panel",
    "System Reset","Installer Programming","Wrong Password","Not Sys Event",
    "Not Sys Event","Extreme Hot Alert","Extreme Hot Alert Restore","Freeze Alert",
    "Freeze Alert Restore","Human Cold Alert","Human Cold Alert Restore","Human Hot Alert",
    "Human Hot Alert Restore","Temperature Sensor Trouble","Temperature Sensor Trouble Restore"
};

const char* PmaxZoneEventTypes[] = {
    "None","Tamper Alarm","Tamper Restore","Open","Closed",
    "Violated (Motion)","Panic Alarm","RF Jamming","Tamper Open",
    "Communication Failure","Line Failure","Fuse","Not Active",
    "Low Battery","AC Failure","Fire Alarm","Emergency",
    "Siren Tamper","Siren Tamper Restore","Siren Low Battery","Siren AC Fail"
};

// MERGED: Extended to cover types 0–16 (was only 0–8).
const char* PmaxPanelType[] = {
    "PowerMax",             //  0
    "PowerMax+",            //  1
    "PowerMax Pro",         //  2
    "PowerMax Complete",    //  3
    "PowerMax Pro Part",    //  4
    "PowerMax Complete Part",//  5
    "PowerMax Express",     //  6
    "PowerMaster 10",       //  7
    "PowerMaster 30",       //  8
    "Unknown (9)",          //  9
    "PowerMaster 33",       // 10
    "Unknown (11)",         // 11
    "Unknown (12)",         // 12
    "PowerMaster 360",      // 13
    "Unknown (14)",         // 14
    "PowerMaster 33",       // 15
    "PowerMaster 360R",     // 16
};

const char* PmaxZoneTypes[] = {
    "Non-Alarm","Emergency","Flood","Gas","Delay 1","Delay 2",
    "Interior-Follow","Perimeter","Perimeter-Follow",
    "24 Hours Silent","24 Hours Audible","Fire","Interior",
    "Home Delay","Temperature","Outdoor","16"
};

const char* PmaxEventSource[] = {
    "System",
    "Zone 1","Zone 2","Zone 3","Zone 4","Zone 5","Zone 6","Zone 7","Zone 8",
    "Zone 09","Zone 10","Zone 11","Zone 12","Zone 13","Zone 14","Zone 15","Zone 16",
    "Zone 17","Zone 18","Zone 19","Zone 20","Zone 21","Zone 22","Zone 23","Zone 24",
    "Zone 25","Zone 26","Zone 27","Zone 28","Zone 29","Zone 30",
    "Fob 1","Fob 2","Fob 3","Fob 4","Fob 5","Fob 6","Fob 7","Fob 8",
    "User 1","User 2","User 3","User 4","User 5","User 6","User 7","User 8",
    "Pad 1","Pad 2","Pad 3","Pad 4","Pad 5","Pad 6","Pad 7","Pad 8",
    "Sir 1","Sir 2",
    "2Pad 1","2Pad 2","2Pad 3","2Pad 4",
    "X10 1","X10 2","X10 3","X10 4","X10 5","X10 6","X10 7","X10 8",
    "X10 9","X10 10","X10 11","X10 12","X10 13","X10 14","X10 15",
    "PGM","GSM","P-LINK",
    "PTag 1","PTag 2","PTag 3","PTag 4","PTag 5","PTag 6","PTag 7","PTag 8"
};

// MERGED: Standard Visonic zone-name string table.
// B0 0x21 (ZONE_NAMES) data bytes are indices into this table.
// Matches HA's pmZoneName[] array (pyvisonic.py).
static const char* const PmZoneNameTable[] = {
    "Attic",           // 0x00
    "Back Door",       // 0x01
    "Basement",        // 0x02
    "Bathroom",        // 0x03
    "Bedroom",         // 0x04
    "Child Room",      // 0x05
    "Conservatory",    // 0x06
    "Play Room",       // 0x07
    "Dining Room",     // 0x08
    "Downstairs",      // 0x09
    "Emergency",       // 0x0A
    "Fire",            // 0x0B
    "Front Door",      // 0x0C
    "Garage",          // 0x0D
    "Garage Door",     // 0x0E
    "Guest Room",      // 0x0F
    "Hall",            // 0x10
    "Kitchen",         // 0x11
    "Laundry Room",    // 0x12
    "Living Room",     // 0x13
    "Master Bathroom", // 0x14
    "Master Bedroom",  // 0x15
    "Office",          // 0x16
    "Upstairs",        // 0x17
    "Utility Room",    // 0x18
    "Yard",            // 0x19
    "Custom 1",        // 0x1A
    "Custom 2",        // 0x1B
    "Custom 3",        // 0x1C
    NULL
};
static const int PmZoneNameTableSize = 29;  // entries before NULL

// MERGED: PowerMaster sensor type lookup table (keyed by device-type byte from masterReadBuff).
// Derived from HA pmZoneMaster dictionary.
struct PmasterSensorEntry { unsigned char id; const char* type; const char* make; };
static const PmasterSensorEntry PmZoneMasterTypes[] = {
    {0x01,"Motion",     "Next PG2"          },
    {0x03,"Motion",     "Clip PG2"          },
    {0x04,"Camera",     "Next CAM PG2"      },
    {0x05,"Sound",      "GB-502 PG2"        },
    {0x06,"Motion",     "TOWER-32AM PG2"    },
    {0x07,"Motion",     "TOWER-32AMK9"      },
    {0x08,"Motion",     "TOWER-20AM PG2"    },
    {0x0A,"Camera",     "TOWER CAM PG2"     },
    {0x0B,"GlassBreak", "GB-502 PG2"        },
    {0x0C,"Motion",     "MP-802 PG2"        },
    {0x0F,"Motion",     "MP-902 PG2"        },
    {0x15,"Smoke",      "SMD-426 PG2"       },
    {0x16,"Smoke",      "SMD-429 PG2"       },
    {0x18,"Smoke",      "GSD-442 PG2"       },
    {0x19,"Flood",      "FLD-550 PG2"       },
    {0x1A,"Temperature","TMD-560 PG2"       },
    {0x1E,"Smoke",      "SMD-429 PG2"       },
    {0x29,"Magnet",     "MC-302V PG2"       },
    {0x2A,"Magnet",     "MC-302 PG2"        },
    {0x2C,"Magnet",     "MC-303V PG2"       },
    {0x2D,"Magnet",     "MC-302V PG2"       },
    {0x35,"Shock",      "SD-304 PG2"        },
    {0xFA,"Magnet",     "MC-302E PG2"       },
    {0xFE,"Wired",      "Wired"             },
    {0x00, NULL, NULL}  // sentinel
};

// ############################################################
// B0 chunk extraction
//
// A chunky B0 message is NOT a flat payload - it carries one or more chunks,
// each with a 4-byte header, and a message can mix device classes (0x1F
// DEVICE_TYPES for example carries a ZONES chunk *and* a SIRENS chunk):
//
//   [0]B0 [1]msgType [2]subType [3]msgLen
//   then repeated: <seq> <datasize> <index> <length> <length bytes of data>
//   then <counter> <0x43>
//
//   datasize = bits per entry (1 = bitmask, 8 = one byte per device)
//   index    = device class    (2 = SIRENS, 3 = ZONES, 255 = MIXED)
//   length   = number of data bytes in THIS chunk
//
// Previously every handler assumed a single chunk and read (Buff->size - 8)
// bytes, which ran the next chunk's header and payload - plus the trailing
// counter and 0x43 - into the zone arrays.
//
// Returns a pointer to the data of the first chunk whose index matches
// wantIndex and writes its length to *lenOut, or NULL if there is no such
// chunk or the message does not parse cleanly.
// ############################################################
#define B0_INDEX_SIRENS 0x02
#define B0_INDEX_ZONES  0x03

static const unsigned char* findB0Chunk(const PlinkBuffer* Buff, unsigned char wantIndex,
                                        int* lenOut, unsigned char* datasizeOut)
{
    *lenOut = 0;
    if(datasizeOut) *datasizeOut = 0;

    if(Buff->size < 7) return NULL;

    const int msgLen = Buff->buffer[3];
    // HA validates len(data) == msgLen + 4 where data excludes the 0x0D and the
    // 0xB0; our buffer keeps the 0xB0, so the equivalent check is size == msgLen + 5.
    if(Buff->size != msgLen + 5)
    {
        DEBUG(LOG_WARNING,"B0 chunk parse: length mismatch (size=%d, expected %d) - not processing",
              Buff->size, msgLen + 5);
        return NULL;
    }

    const int end = Buff->size - 2;   // exclusive: last two bytes are <counter> <0x43>
    const unsigned char* found = NULL;

    int pos = 4;
    while(pos + 4 <= end)
    {
        const unsigned char datasize = Buff->buffer[pos+1];
        const unsigned char index    = Buff->buffer[pos+2];
        const unsigned char length   = Buff->buffer[pos+3];

        if(pos + 4 + length > end)
        {
            DEBUG(LOG_WARNING,"B0 chunk parse: chunk overruns message, discarding");
            return NULL;
        }

        if(index == wantIndex && found == NULL)
        {
            found = &Buff->buffer[pos+4];
            *lenOut = length;
            if(datasizeOut) *datasizeOut = datasize;
        }
        pos += length + 4;
    }

    // The walk must land exactly on the end of the chunk region, otherwise we
    // have misparsed and cannot trust anything we extracted.
    if(pos != end)
    {
        DEBUG(LOG_WARNING,"B0 chunk parse: ended at %d, expected %d - discarding", pos, end);
        return NULL;
    }

    return found;
}

static void lookupPmasterSensor(unsigned char sensorId, const char** typeOut, const char** makeOut)
{
    for(int i=0; PmZoneMasterTypes[i].make != NULL; i++)
    {
        if(PmZoneMasterTypes[i].id == sensorId)
        {
            *typeOut = PmZoneMasterTypes[i].type;
            *makeOut = PmZoneMasterTypes[i].make;
            return;
        }
    }
    *typeOut = "Unknown";
    *makeOut = "Visonic Unknown";
}

IMPEMENT_GET_FUNCTION(PmaxSystemStatus);
IMPEMENT_GET_FUNCTION(SystemStateFlags);
IMPEMENT_GET_FUNCTION(PmaxZoneEventTypes);
IMPEMENT_GET_FUNCTION(PmaxLogEvents);
IMPEMENT_GET_FUNCTION(PmaxPanelType);
IMPEMENT_GET_FUNCTION(PmaxZoneTypes);
IMPEMENT_GET_FUNCTION(PmaxEventSource);

// ############################################################
// Download-code ladder
//
// Order is deliberate.
//
// The code we send in ENROLLREPLY is *registered* by the panel as its Powerlink
// code (it lands in the EPROM at page 0x02 index 0x12) and is then accepted for
// later download requests. So the value is largely arbitrary once paired - what
// matters is getting in the first time, which needs either a factory default or
// whatever this bridge registered previously.
//
// That is why 3622 is in the list at all: it was this project's hard-coded
// default for years, so any panel already paired by an older build has 3622
// registered and will still accept it even if the installer has changed the
// panel's own download code away from factory. Do not remove it.
// ############################################################
static const int PmDownloadCodeLadder[DL_CODE_LADDER_LEN] =
{
    0x5650,   // PowerMax factory default
    0xAAAA,   // PowerMaster factory default
    0xBBBB,   // alternative default seen on some panels
    0x3622    // registered by older builds of this project - keep last
};

int PowerMaxAlarm::getDownloadCode() const
{
    if(m_iDlCodeIndex < 0)
    {
        return (int)Powerlink_DownloadCode_Override;
    }
    return PmDownloadCodeLadder[m_iDlCodeIndex % DL_CODE_LADDER_LEN];
}

void PowerMaxAlarm::resetDownloadCode()
{
    // Start from the user override when one is configured, otherwise the ladder.
    m_iDlCodeIndex    = (Powerlink_DownloadCode_Override == DL_CODE_NO_OVERRIDE) ? 0 : -1;
    m_iDlCodeAdvances = 0;
    m_iDlCodeRetry    = 0;
}

bool PowerMaxAlarm::advanceDownloadCode()
{
    if(m_iDlCodeAdvances >= DL_CODE_LADDER_LEN * DL_CODE_MAX_PASSES)
    {
        DEBUG(LOG_ERR,"Download code ladder exhausted after %d attempts. "
                      "Check the download code programmed in your panel (Installer menu).",
              m_iDlCodeAdvances);
        return false;
    }
    m_iDlCodeAdvances++;

    // -1 (override) moves to the start of the ladder; otherwise step through it.
    m_iDlCodeIndex = (m_iDlCodeIndex < 0) ? 0 : ((m_iDlCodeIndex + 1) % DL_CODE_LADDER_LEN);

    DEBUG(LOG_INFO,"Access Denied on DL_START, trying next download code %04X (attempt %d)",
          getDownloadCode(), m_iDlCodeAdvances);
    return true;
}

// ############################################################
// init
// ############################################################
void PowerMaxAlarm::init(int initWaitTime)
{
    memset(&m_lastSentCommand, 0, sizeof(m_lastSentCommand));
    m_bEnrolCompleted        = false;
    m_bDownloadMode          = false;
    m_iPanelType             = -1;
    m_iModelType             = 0;
    m_bPowerMaster           = false;
    m_bPmZoneDataRequested   = false;  // MERGED: reset so first B0 0x39 triggers zone-init request
    resetDownloadCode();               // always restart the ladder from the user override
    m_bAbSupported           = true;   // safe default until the 0x3C tells us the panel type
    m_bAutoEnrol             = false;  // do not send AB 0A until we know the panel accepts it
    m_bInitSupported         = false;  // likewise for INIT
    m_bPowerlinkAlive        = false;
    m_iEnrolAttempts         = 0;
    m_ulNextEnrolAttempt     = 0;
    m_partitionMask          = 0x07;   // all partitions until the EPROM tells us otherwise
    m_bPartitionsEnabled     = false;
    m_ackTypeForLastMsg   = ACK_1;
    m_ulLastPing          = os_getCurrentTimeSec();
    m_ulNextPingDeadline  = ULONG_MAX;
    m_iInitWaitTime       = initWaitTime;

    flags     = 0;
    stat      = SS_Not_Ready;
    alarmState= 0;
    memset(alarmTrippedZones, 0, sizeof(alarmTrippedZones));
    lastIoTime = 0;
    memset(zone, 0, sizeof(zone));

    // Exit any lingering download mode first.
    PowerMaxAlarm::sendCommand(Pmax_DL_EXIT);

    // NOTE: we deliberately do NOT send INIT (AB 0A 00 01) here.
    //
    // Whether a panel supports INIT depends on its type, and the panel type only
    // arrives in the 0x3C reply to the DL_START below - so at this point we cannot
    // know.  The HA integration has the same problem and resolves it the same way:
    // it gates INIT on pmInitSupportedByPanel, which is False until a 0x3C has been
    // received, so a first connection never sends it.
    //
    // Sending it here also put it 300ms ahead of DL_START, close enough that a
    // rejection of the INIT could be mistaken for a rejection of the download code,
    // and close enough that DL_START could land while the panel was still busy.
    PowerMaxAlarm::sendCommand(Pmax_DL_START);
}

unsigned int PowerMaxAlarm::getEnrolledZoneCnt() const
{
    unsigned int cnt = 0;
    for(unsigned char i=1; i<MAX_ZONE_COUNT; i++)
    {
        if(zone[i].enrolled) cnt++;
    }
    return cnt;
}

unsigned long PowerMaxAlarm::getSecondsFromLastComm() const
{
    return (unsigned long)(os_getCurrentTimeSec()-lastIoTime);
}

// Insert the *user* PIN (arm / disarm / bypass / event log).
//
// A configured override always wins.  That matters because the code we read out of
// the EPROM is not always the one the panel will accept for arming, and without an
// override there is no way for the user to correct it or even to test another value.
// With no override we use the panel's own code, which is the normal case.
void PowerMaxAlarm::addPin(unsigned char* bufferToSend, int pos, bool useMasterCode)
{
    int pin = 0;

    if(Powerlink_User_PIN_Code != USER_PIN_NO_OVERRIDE)
    {
        pin = (int)Powerlink_User_PIN_Code;
    }
    else if((!Powerlink_ListenNotEnrol) && useMasterCode)
    {
        pin = m_cfg.GetMasterPinAsHex();
    }

    if(pin == 0)
    {
        // Listen-only mode, or the EPROM download did not give us the user codes.
        // Sending 0000 will be denied by the panel, so say why.
        DEBUG(LOG_WARNING,"No user PIN available (none read from the panel and no override set). "
                          "The panel will reject this command - set a User PIN on the settings page.");
    }

    bufferToSend[pos]   = pin >> 8;
    bufferToSend[pos+1] = pin & 0x00FF;
}

// AB 0A 00 01 - the panel is asking us to enrol (installer has started enrolment at
// the keypad).  Note this is the exception, not the rule: see tryEnrolPowerlink().
void PowerMaxAlarm::OnEnroll(const PlinkBuffer* Buff)
{
    this->clearQueue();
    this->sendCommand(Pmax_ENROLLREPLY);
    DEBUG(LOG_INFO,"Enrolling.....");
    this->sendCommand(Pmax_DL_START);
    // MERGED: Reset zone-data flag so the next B0 0x39 re-requests zone init after re-enrolment.
    m_bPmZoneDataRequested = false;
}

// ############################################################
// powerLinkEnrolled – called once the panel enters download mode
// ############################################################
void PowerMaxAlarm::powerLinkEnrolled()
{
    m_bEnrolCompleted = true;

    if(!m_bPowerMaster)
    {
        // PowerMax only: request EPROM download blocks for firmware string, serial number,
        // and zone-name string table.  PowerMaster (PM30+) does not support these EPROM
        // read commands and responds with spurious A5 / unrecognised messages instead.
        sendCommand(Pmax_DL_PANELFW);
        sendCommand(Pmax_DL_SERIAL);
        sendCommand(Pmax_DL_ZONESTR);
    }

    sendCommand(Pmax_DL_GET);

    unsigned char year, month, day, hour, minutes, seconds;
    if(os_getLocalTime(year, month, day, hour, minutes, seconds))
    {
        setDateTime(year, month, day, hour, minutes, seconds);
    }

    sendCommand(Pmax_DL_EXIT);

    if(m_bPowerMaster == false)
    {
        // Event log only for PowerMax (PowerMaster uses B0 event log)
        // this->sendCommand(Pmax_GETEVENTLOG);  // uncomment to enable
    }
}

// ############################################################
// OnPanelInfo  (0x3C response after DL_START)
// ############################################################
void PowerMaxAlarm::OnPanelInfo(const PlinkBuffer* Buff)
{
    this->m_iPanelType = Buff->buffer[6];
    this->m_iModelType = Buff->buffer[5];
    this->m_bPowerMaster = (this->m_iPanelType >= 7);
    // The download code we are currently on is the one the panel just accepted.

    // Learn what this panel type can actually do.  Until the 0x3C arrives we assume
    // AB is supported (true for every panel except the 360/360R) and that we cannot
    // auto-enrol, so we never send AB 0A to a panel that would reject it.
    {
        const unsigned char tblAb[]   = VCFG_AB_SUPPORTED;
        const unsigned char tblAuto[] = VCFG_AUTO_ENROL;
        const unsigned char tblInit[] = VCFG_INIT_SUPPORT;
        const int cnt = (int)(sizeof(tblAb)/sizeof(tblAb[0]));
        if(this->m_iPanelType >= 0 && this->m_iPanelType < cnt)
        {
            this->m_bAbSupported   = (tblAb[this->m_iPanelType]   != 0);
            this->m_bAutoEnrol     = (tblAuto[this->m_iPanelType] != 0);
            this->m_bInitSupported = (tblInit[this->m_iPanelType] != 0);
        }
        DEBUG(LOG_INFO,"Panel capabilities: AB messages=%s, auto-enrol=%s, INIT=%s",
              this->m_bAbSupported   ? "yes" : "no",
              this->m_bAutoEnrol     ? "yes" : "no",
              this->m_bInitSupported ? "yes" : "no");
    }

    this->sendCommand(Pmax_ACK);

    DEBUG(LOG_INFO,"Download code %04X accepted by panel", this->getDownloadCode());
    DEBUG(LOG_INFO,"Received Panel Info. PanelType: %s, Model=%d (0x%X)",
          GetStrPmaxPanelType(this->m_iPanelType),
          this->m_iModelType,
          (this->m_iPanelType * 0x100 + this->m_iModelType));

    this->powerLinkEnrolled();
}

// ############################################################
// Memory map helpers (unchanged from original)
// ############################################################
int PowerMaxAlarm::readMemoryMap(const unsigned char* sData, unsigned char* buffOut, int buffOutSize)
{
    int iPage   = sData[2];
    int iIndex  = sData[1];
    int iLength = (sData[4] * 0x100) + sData[3];

    if(iLength > buffOutSize)
    {
        DEBUG(LOG_ERR,"readMemoryMap, buffer too small, needed: %d, got: %d", iLength, buffOutSize);
        return 0;
    }

    MemoryMap* pMap = &m_mapMain;
    if(iPage == 0xFF && iIndex == 0xFF)
    {
        pMap   = &m_mapExtended;
        iPage  = sData[7];
        iIndex = sData[6];
    }
    return pMap->Read(iPage, iIndex, iLength, buffOut);
}

void PowerMaxAlarm::writeMemoryMap(int iPage, int iIndex, const unsigned char* sData, int sDataLen)
{
    MemoryMap* pMap = &m_mapMain;
    if(iPage == 0xFF && iIndex == 0xFF)
    {
        pMap      = &m_mapExtended;
        iPage     = sData[1];
        iIndex    = sData[0];
        sData    += 4;
        sDataLen -= 4;
    }
    int bytesWritten = pMap->Write(iPage, iIndex, sDataLen, sData);
    if(bytesWritten != sDataLen)
    {
        DEBUG(LOG_ERR,"Failed to write to memory, page: %d, index: %d, len: %d, got: %d",
              iPage, iIndex, sDataLen, bytesWritten);
    }
}

void PowerMaxAlarm::OnDownloadInfo(const PlinkBuffer* Buff)
{
    this->sendCommand(Pmax_ACK);
    int iIndex  = Buff->buffer[1];
    int iPage   = Buff->buffer[2];
    int iLength = Buff->buffer[3];
    if(iLength != Buff->size-4)
    {
        DEBUG(LOG_WARNING,"Received Download Data with invalid len indication: %d, got: %d",
              iLength, Buff->size-4);
    }
    this->writeMemoryMap(iPage, iIndex, Buff->buffer+4, Buff->size-4);
}

void PowerMaxAlarm::OnDownloadSettings(const PlinkBuffer* Buff)
{
    this->sendCommand(Pmax_ACK);
    int iIndex = Buff->buffer[1];
    int iPage  = Buff->buffer[2];
    this->writeMemoryMap(iPage, iIndex, Buff->buffer+3, Buff->size-3);
}

void PowerMaxAlarm::OnStop(const PlinkBuffer* Buff)
{
    this->sendCommand(Pmax_ACK);
    DEBUG(LOG_INFO,"Stop (Dload complete)");
}

// ############################################################
// tryEnrolPowerlink
//
// Ask the panel to enrol us as a Powerlink module.  This has to be done on our own
// initiative: the panel only sends an unsolicited enrol request (AB 0A 00 01, see
// OnEnroll) when the installer starts enrolment at the keypad.  In normal operation
// it never asks, so if we only ever wait for it we stay un-enrolled forever - which
// is exactly what the telnet captures showed happening on PowerMaster panels.
//
// Mirrors sendMsgENROL() in the HA integration: send AB 0A with the download code,
// then a RESTORE.  The panel confirms by starting to send its own AB 03 keep-alives,
// which is what sets m_bPowerlinkAlive in OnPing.
// ############################################################
void PowerMaxAlarm::tryEnrolPowerlink()
{
    if(Powerlink_ListenNotEnrol) return;      // user asked us to stay passive
    if(!m_bAutoEnrol)
    {
        // Older PowerMax panels have to be enrolled from the installer menu.
        DEBUG(LOG_INFO,"Panel does not support auto-enrol; enrol from the installer menu to get Powerlink");
        return;
    }
    if(!m_bAbSupported) return;               // 360 / 360R cannot do AB messages at all

    if(m_iEnrolAttempts >= MAX_ENROL_ATTEMPTS)
    {
        m_ulNextEnrolAttempt = 0;             // stop retrying, carry on in listen mode
        DEBUG(LOG_WARNING,"Powerlink enrolment not confirmed after %d attempts, continuing without it",
              m_iEnrolAttempts);
        return;
    }

    m_iEnrolAttempts++;
    m_ulNextEnrolAttempt = os_getCurrentTimeSec() + ENROL_RETRY_SECONDS;
    DEBUG(LOG_INFO,"Requesting Powerlink enrolment (attempt %d of %d) with download code %04X",
          m_iEnrolAttempts, MAX_ENROL_ATTEMPTS, getDownloadCode());

    sendCommand(Pmax_ENROLLREPLY);
    sendCommand(Pmax_RESTORE);
}

void PowerMaxAlarm::startKeepAliveTimer()
{
    m_ulNextPingDeadline = os_getCurrentTimeSec() + 180;
}

void PowerMaxAlarm::stopKeepAliveTimer()
{
    m_ulNextPingDeadline = ULONG_MAX;
}

bool PowerMaxAlarm::restoreCommsIfLost()
{
    // Powerlink enrolment retry.  We asked the panel to enrol us but it has not started
    // sending its AB 03 keep-alives, so ask again (bounded by MAX_ENROL_ATTEMPTS).
    if((m_bDownloadMode == false) && (m_bPowerlinkAlive == false) &&
       (m_ulNextEnrolAttempt != 0) && (os_getCurrentTimeSec() > m_ulNextEnrolAttempt))
    {
        tryEnrolPowerlink();
        return true;
    }

    if((m_bDownloadMode == false) && (os_getCurrentTimeSec() > m_ulNextPingDeadline))
    {
        sendCommand(Pmax_RESTORE);
        startKeepAliveTimer();
        return true;
    }
    return false;
}

// AB 03 from the panel.  This is the panel telling us it considers us enrolled, so
// it is the only reliable confirmation that Powerlink is actually established.
void PowerMaxAlarm::OnPing(const PlinkBuffer* Buff)
{
    this->m_bDownloadMode = false;
    this->sendCommand(Pmax_ACK);
    if(this->m_bPowerlinkAlive == false)
    {
        this->m_bPowerlinkAlive   = true;
        this->m_ulNextEnrolAttempt = 0;   // enrolled, stop retrying
        DEBUG(LOG_INFO,"Powerlink established (first keep-alive received from panel)");
    }
    DEBUG(LOG_INFO,"Ping.....");
    this->startKeepAliveTimer();
}

// ############################################################
// processSettings – parses downloaded EPROM data
// MERGED: panel-type limit raised from 8 to 16; PM zone types decoded; VCFG extended
// ############################################################
void PowerMaxAlarm::processSettings()
{
    m_cfg.Init();
    unsigned char readBuff[512] = {0};

    if(m_iPanelType == -1)
    {
        DEBUG(LOG_WARNING,"ERROR: Can't process settings, the PanelType is unknown");
        return;
    }

    // MERGED: was `> 8`, now `> 16` to support PM33 (10/15), PM360 (13), PM360R (16)
    if(m_iPanelType > 16)
    {
        DEBUG(LOG_WARNING,"ERROR: Can't process settings, unrecognised PanelType= %d", m_iPanelType);
        return;
    }

    // Read serial number and verify panel type
    {
        const unsigned char msg[] = VMSG_DL_SERIAL;
        const int readCnt = readMemoryMap(msg, readBuff, sizeof(readBuff));
        if(readCnt < 8)
        {
            if(!m_bPowerMaster)
            {
                DEBUG(LOG_WARNING,"ERROR: Can't read the PowerMax/Master MemoryMap, comms failed?");
                return;
            }
            // PowerMaster (PM30+) skips EPROM download so the memory map is empty – this is normal.
            DEBUG(LOG_INFO,"PowerMaster: EPROM serial not available (PM30+ skips download), continuing with B0 data");
            m_cfg.parsedOK = true;
        }
        else
        {
            if(readBuff[7] != m_iPanelType)
            {
                if(!m_bPowerMaster)
                {
                    DEBUG(LOG_WARNING,"ERROR: PanelType mismatch. Expected=%d, Read=%d",
                          m_iPanelType, readBuff[7]);
                    return;
                }
                DEBUG(LOG_WARNING,"PowerMaster: EPROM panel-type mismatch (expected=%d got=%d), using OnPanelInfo value",
                      m_iPanelType, readBuff[7]);
            }
            sprintf(m_cfg.serialNumber,"%02X%02X%02X%02X%02X%02X",
                    readBuff[0],readBuff[1],readBuff[2],readBuff[3],readBuff[4],readBuff[5]);
            char* end = strchr(m_cfg.serialNumber,'F');
            if(end) *end = '\0';
            m_cfg.parsedOK = true;
        }
    }

    // Panel capabilities (MERGED: now uses extended 18-entry VCFG tables)
    {
        unsigned char tmpPARTITIONS[] = VCFG_PARTITIONS;
        unsigned char tmpKEYFOBS[]    = VCFG_KEYFOBS;
        unsigned char tmp1WKEYP[]     = VCFG_1WKEYPADS;
        unsigned char tmp2WKEYP[]     = VCFG_2WKEYPADS;
        unsigned char tmpSIRENS[]     = VCFG_SIRENS;
        unsigned char tmpUSERS[]      = VCFG_USERCODES;
        unsigned char tmpWIRELESS[]   = VCFG_WIRELESS;
        unsigned char tmpWIRED[]      = VCFG_WIRED;
        unsigned char tmpCUSTOM[]     = VCFG_ZONECUSTOM;

        m_cfg.maxZoneCnt      = tmpWIRELESS[m_iPanelType] + tmpWIRED[m_iPanelType];
        m_cfg.maxCustomCnt    = tmpCUSTOM[m_iPanelType];
        m_cfg.maxUserCnt      = tmpUSERS[m_iPanelType];
        m_cfg.maxPartitionCnt = tmpPARTITIONS[m_iPanelType];
        m_cfg.partitionCnt    = tmpPARTITIONS[m_iPanelType];
        m_cfg.maxSirenCnt     = tmpSIRENS[m_iPanelType];
        m_cfg.maxKeypad1Cnt   = tmp1WKEYP[m_iPanelType];
        m_cfg.maxKeypad2Cnt   = tmp2WKEYP[m_iPanelType];
        m_cfg.maxKeyfobCnt    = tmpKEYFOBS[m_iPanelType];
    }

    // EPROM and software version
    {
        const unsigned char msg[] = VMSG_DL_PANELFW;
        const int readCnt = readMemoryMap(msg, readBuff, sizeof(readBuff));
        if(readCnt >= 16) strncpy(m_cfg.eprom,    (const char*)readBuff,    sizeof(m_cfg.eprom)-1);
        if(readCnt >= 32) strncpy(m_cfg.software, (const char*)readBuff+16, sizeof(m_cfg.software)-1);
    }

    // Zone name strings
    char zoneNames[MAX_ZONE_COUNT][0x11];
    memset(zoneNames, 0, sizeof(zoneNames));
    {
        const unsigned char msg[] = VMSG_DL_ZONESTR;
        const int readCnt = readMemoryMap(msg, readBuff, sizeof(readBuff));
        if(readCnt == 0)
        {
            DEBUG(LOG_WARNING,"ERROR: Failed to read zone names");
        }
        else
        {
            for(int iCnt=0; iCnt<MAX_ZONE_COUNT; iCnt++)
            {
                if((iCnt*0x10)+0x10 <= readCnt)
                {
                    memcpy(zoneNames[iCnt], readBuff+(iCnt*0x10), 0x10);
                    if((unsigned char)zoneNames[iCnt][0] == 0xFF)
                        zoneNames[iCnt][0] = '\0';
                }
            }
        }
    }

    // Telephone numbers
    {
        const unsigned char msg[] = VMSG_DL_PHONENRS;
        const int readCnt = readMemoryMap(msg, readBuff, sizeof(readBuff));
        for(int iCnt=0; iCnt<4; iCnt++)
        {
            memset(m_cfg.phone[iCnt], 0, sizeof(m_cfg.phone[iCnt]));
            for(int jCnt=0; jCnt<=7; jCnt++)
            {
                if(readBuff[8*iCnt+jCnt] != 0xFF)
                {
                    char szTwo[10] = "";
                    sprintf(szTwo,"%02X",readBuff[8*iCnt+jCnt]);
                    os_strncat_s(m_cfg.phone[iCnt],sizeof(m_cfg.phone[iCnt]),szTwo);
                }
                char* pEnd = strchr(m_cfg.phone[iCnt],'F');
                if(pEnd != NULL) *pEnd = '\0';
            }
        }
    }

    // Alarm settings
    {
        const unsigned char msg[] = VMSG_DL_COMMDEF;
        const int readCnt = readMemoryMap(msg, readBuff, sizeof(readBuff));
        if(readCnt < 30)
            DEBUG(LOG_WARNING,"ERROR: Failed to read alarm settings");
    }

    // Partition enabled, and which partitions are actually in use.
    //
    // VMSG_DL_PARTITIONS reads 0xF0 bytes from EPROM byte 768 (page 0x03 index 0):
    //    +0x00        PART_ENABLED   non-zero (and not 0xFF) when partitions are used
    //    +0x11..+0x50 PART_ZONE_DATA one byte per zone, a partition bitmask
    //                                (1 = P1, 2 = P2, 4 = P3, OR'd for shared zones)
    // Offsets from the HA integration's EPROM map (EPROM.PART_ENABLED byte 768,
    // EPROM.PART_ZONE_DATA byte 785).
    if(m_cfg.maxPartitionCnt > 0)
    {
        const unsigned char msg[] = VMSG_DL_PARTITIONS;
        const int readCnt = readMemoryMap(msg, readBuff, sizeof(readBuff));
        if(readCnt > 0 && readBuff[0] == 0)
            m_cfg.partitionCnt = 1;

        const int partZoneOffset = 0x11;
        if(readCnt > 0 && readBuff[0] != 0 && readBuff[0] != 0xFF &&
           readCnt >= partZoneOffset + MAX_ZONE_COUNT)
        {
            // OR together every zone's partition bitmask to find which partitions the
            // panel actually uses. Ignore 0xFF, which is "no zone here".
            unsigned char mask = 0;
            for(int z = 0; z < MAX_ZONE_COUNT; z++)
            {
                const unsigned char v = readBuff[partZoneOffset + z];
                if(v != 0xFF) mask |= v;
            }
            mask &= 0x07;
            if(mask != 0)
            {
                m_partitionMask = mask;
                m_bPartitionsEnabled = true;
                DEBUG(LOG_INFO,"Panel uses partitions, arm/disarm will target mask 0x%02X", mask);
            }
        }
    }

    // Panel date/time
    {
        unsigned char dateAndTime[32] = {0};
        const unsigned char msg[] = VMSG_DL_TIME;
        int bytesRead = readMemoryMap(msg, dateAndTime, sizeof(dateAndTime));
        if(bytesRead >= 6)
            OnPanelDateTime(dateAndTime[5],dateAndTime[4],dateAndTime[3],
                            dateAndTime[2],dateAndTime[1],dateAndTime[0]);
    }

    // Zone data
    {
        unsigned char masterReadBuff[640] = {0};
        const unsigned char msg[] = VMSG_DL_ZONES;
        const int readCnt = readMemoryMap(msg, readBuff, sizeof(readBuff));
        if(readCnt < 120)
        {
            DEBUG(LOG_WARNING,"ERROR: Failed to read zone settings");
        }
        else
        {
            int zoneNameIdxCnt = 0;
            unsigned char zoneNamesIndexes[MAX_ZONE_COUNT] = {0};

            unsigned char zoneSignal[MAX_ZONE_COUNT];
            memset(zoneSignal, 0xFF, sizeof(zoneSignal));

            if(m_bPowerMaster)
            {
                const unsigned char msgN[] = VMSG_DL_MASTER_ZONENAMES;
                zoneNameIdxCnt = readMemoryMap(msgN, zoneNamesIndexes, sizeof(zoneNamesIndexes));

                const unsigned char msgZ[] = VMSG_DL_MASTER_ZONES;
                readMemoryMap(msgZ, masterReadBuff, sizeof(masterReadBuff));
            }
            else
            {
                const unsigned char msgN[] = VMSG_DL_ZONENAMES;
                zoneNameIdxCnt = readMemoryMap(msgN, zoneNamesIndexes, sizeof(zoneNamesIndexes));
                if(zoneNameIdxCnt != (int)sizeof(zoneNamesIndexes))
                    DEBUG(LOG_WARNING,"ERROR: Failed to read zone name indexes");
            }

            {
                const unsigned char msgSig[] = VMSG_DL_ZONESIGNAL;
                int zoneCnt = readMemoryMap(msgSig, zoneSignal, sizeof(zoneSignal));
                if(zoneCnt == 0)
                    DEBUG(LOG_WARNING,"ERROR: Failed to read zone signal strength");
            }

            strcpy(zone[0].name,"System");
            zone[0].signalStrength = 0xFF;

            for(int iCnt=1; iCnt<=m_cfg.maxZoneCnt; iCnt++)
            {
                if(iCnt >= MAX_ZONE_COUNT)
                {
                    DEBUG(LOG_WARNING,"ERROR: Zone count exceeds MAX_ZONE_COUNT (%d). Increase it.", MAX_ZONE_COUNT);
                    break;
                }

                Zone* pZone = &zone[iCnt];

                // Determine if zone is enrolled
                if(m_bPowerMaster)
                {
                    // PowerMaster: 10 bytes per zone in masterReadBuff
                    int base = (iCnt-1) * 10;
                    if(base+9 < (int)sizeof(masterReadBuff))
                    {
                        pZone->enrolled = masterReadBuff[base+0] != 0 ||
                                          masterReadBuff[base+1] != 0 ||
                                          masterReadBuff[base+2] != 0 ||
                                          masterReadBuff[base+3] != 0 ||
                                          masterReadBuff[base+4] != 0;
                    }
                }
                else
                {
                    pZone->enrolled = readBuff[iCnt*4-4] != 0 ||
                                      readBuff[iCnt*4-3] != 0 ||
                                      readBuff[iCnt*4-2] != 0;
                }

                if(pZone->enrolled == false) continue;

                // Zone name
                int zoneIndex = (iCnt-1 < zoneNameIdxCnt) ? zoneNamesIndexes[iCnt-1] : 0xFF;
                if(zoneIndex < MAX_ZONE_COUNT)
                {
                    strcpy(pZone->name, zoneNames[zoneIndex]);
                    // trim trailing whitespace
                    int len = strlen(pZone->name);
                    while(len > 0 && isspace(pZone->name[len-1]))
                        pZone->name[--len] = '\0';
                }
                else
                {
                    strcpy(pZone->name,"Unknown");
                }

                // Signal strength
                pZone->signalStrength = (iCnt-1 < (int)sizeof(zoneSignal)) ? zoneSignal[iCnt-1] : 0xFF;

                // Sensor type / zone type
                if(m_bPowerMaster)
                {
                    // MERGED: PowerMaster zone data is 10 bytes per zone in masterReadBuff.
                    // Byte 0 = device type ID → look up in PmZoneMasterTypes table.
                    // Byte 3 lower nibble = zone type (same indices as PmaxZoneTypes).
                    int base = (iCnt-1) * 10;
                    pZone->sensorId   = masterReadBuff[base+0];
                    pZone->zoneType   = masterReadBuff[base+3] & 0x0F;
                    pZone->zoneTypeStr = GetStrPmaxZoneTypes(pZone->zoneType);
                    lookupPmasterSensor(pZone->sensorId, &pZone->sensorType, &pZone->sensorMake);
                }
                else
                {
                    // Only the low nibble is the zone type; the upper nibble carries
                    // something else (not yet identified) and is set on some zones.
                    // Without the mask a value like 0x24 falls off the end of the
                    // 17-entry type table and shows as "??" instead of "Delay 1".
                    // The PowerMaster branch above has always masked this.
                    pZone->zoneType    = readBuff[iCnt*4-1] & 0x0F;
                    pZone->zoneTypeStr = GetStrPmaxZoneTypes(pZone->zoneType);
                    pZone->sensorId    = readBuff[iCnt*4-2];

                    switch(pZone->sensorId & 0xF)
                    {
                    case 0x0: pZone->sensorType="Vibration"; pZone->sensorMake="Visonic Vibration Sensor"; break;
                    case 0x2: pZone->sensorType="Shock";     pZone->sensorMake="Visonic Shock Sensor";     break;
                    case 0x3: case 0x4: case 0xC:
                              pZone->sensorType="Motion";    pZone->sensorMake="Visonic PIR";               break;
                    case 0x5: case 0x6: case 0x7:
                              pZone->sensorType="Magnet";    pZone->sensorMake="Visonic Door/Window Contact";break;
                    case 0xA: pZone->sensorType="Smoke";     pZone->sensorMake="Visonic Smoke Detector";    break;
                    case 0xB: pZone->sensorType="Gas";       pZone->sensorMake="Visonic Gas Detector";      break;
                    case 0xF: pZone->sensorType="Wired";     pZone->sensorMake="Visonic PIRWired";           break;
                    default:  pZone->sensorType="Unknown";   pZone->sensorMake="Visonic Unknown";            break;
                    }
                }
            }
        }
    }

    // User PIN codes
    {
        int readCnt;
        if(m_bPowerMaster)
        {
            const unsigned char msg[] = VMSG_DL_MASTER_USERPINCODES;
            readCnt = readMemoryMap(msg, readBuff, sizeof(readBuff));
        }
        else
        {
            const unsigned char msg[] = VMSG_DL_USERPINCODES;
            readCnt = readMemoryMap(msg, readBuff, sizeof(readBuff));
        }
        if(readCnt != m_cfg.maxUserCnt*2)
        {
            DEBUG(LOG_WARNING,"ERROR: Failed to read user codes. Expected len: %d, got: %d",
                  m_cfg.maxUserCnt*2, readCnt);
        }
        else
        {
            for(int ix=0; ix<m_cfg.maxUserCnt; ix++)
                sprintf(m_cfg.userPins[ix],"%02X%02X",readBuff[ix*2+0],readBuff[ix*2+1]);
        }
    }

    // Installer / powerlink PIN codes
    {
        // 10 bytes starting at EPROM byte 522 (page 0x02, index 0x0A):
        //   +0 master code, +2 installer code, +4 master download code,
        //   +6 installer download code, +8 the code the powerlink module registered.
        const unsigned char msg[] = VMSG_DL_OTHERPINCODES;
        const int readCnt = readMemoryMap(msg, readBuff, sizeof(readBuff));
        if(readCnt >=  2) sprintf(m_cfg.masterCode,            "%02X%02X",readBuff[0],readBuff[1]);
        if(readCnt >=  4) sprintf(m_cfg.installerCode,         "%02X%02X",readBuff[2],readBuff[3]);
        if(readCnt >=  6) sprintf(m_cfg.masterDownloadCode,    "%02X%02X",readBuff[4],readBuff[5]);
        if(readCnt >=  8) sprintf(m_cfg.installerDownloadCode, "%02X%02X",readBuff[6],readBuff[7]);
        if(readCnt >= 10) sprintf(m_cfg.powerlinkCode,         "%02X%02X",readBuff[8],readBuff[9]);
    }
}

// ############################################################
// ACK / timeout handlers
// ############################################################
void PowerMaxAlarm::OnAck(const PlinkBuffer* Buff)
{
    if(this->m_lastSentCommand.size == 12 &&
       this->m_lastSentCommand.buffer[0] == 0xAB &&
       this->m_lastSentCommand.buffer[1] == 0x0A &&
       this->m_lastSentCommand.buffer[3] == 0x01)
    {
        os_usleep(this->m_iInitWaitTime * 1000000);
    }

    if(this->m_lastSentCommand.size == 1 &&
       this->m_lastSentCommand.buffer[0] == 0x0F)
    {
        this->m_bDownloadMode = false;
        if(this->m_bEnrolCompleted)
        {
#ifdef _MSC_VER
            saveMapToFile("main.map",&this->m_mapMain);
            saveMapToFile("ext.map", &this->m_mapExtended);
#endif
            this->processSettings();
            // Ask the panel to enrol us (sends AB 0A + RESTORE).  The panel almost never
            // asks us first, so without this we stay out of Powerlink permanently.
            // Falls back to a plain RESTORE for panels that cannot auto-enrol.
            if(this->m_bAutoEnrol && !this->Powerlink_ListenNotEnrol)
                this->tryEnrolPowerlink();
            else
                this->sendCommand(Pmax_RESTORE);
            // MERGED: For PowerMaster, request B0 zone data after enrolment.
            // Zone enrollment (0x1D), device types (0x1F), zone names (0x21),
            // zone types (0x2D) and panel state (0x24) all arrive via B0 messages
            // on PM30+ panels – they are never in the EPROM download.
            if(this->m_bPowerMaster)
            {
                const unsigned char subtypes[] = {0x1D, 0x1F, 0x21, 0x2D, 0x24};
                this->sendPmB0Request(subtypes, 5);
            }
        }
        this->startKeepAliveTimer();
    }
}

void PowerMaxAlarm::OnTimeOut(const PlinkBuffer* Buff)
{
    if(this->m_bDownloadMode)
        this->sendCommand(Pmax_DL_EXIT);
    else
        this->sendCommand(Pmax_ACK);
    DEBUG(LOG_INFO,"Time Out");
}

// Access Denied (0x08).
//
// The panel sends this for two very different reasons and they must not be confused:
//   a) DL_START (0x24) with the wrong *download* code  -> try the next code
//   b) a command such as 0xA1 arm/disarm with the wrong *user* PIN -> nothing we
//      can usefully do automatically, just report it
// We tell them apart by looking at what was actually last transmitted.
void PowerMaxAlarm::OnAccessDenied(const PlinkBuffer* Buff)
{
    // Only treat this as a rejected download code when the last thing we actually put on
    // the wire was a DL_START (0x24).  m_lastSentCommand is written by sendBuffer(), so
    // it reflects what was really transmitted - unlike a flag set at queue time, which
    // would still be set while init()'s DL_EXIT (0x0F) and INIT (AB 0A 00 01) are going
    // out ahead of the DL_START, and would blame their rejection on a download code that
    // had not been tried yet.  This mirrors the HA integration, which attributes an
    // Access Denied by inspecting the last command it sent.
    const unsigned char lastCmd = (m_lastSentCommand.size > 0) ? m_lastSentCommand.buffer[0] : 0x00;

    DEBUG(LOG_INFO,"Access denied (last command sent was 0x%02X)", lastCmd);

    if(lastCmd == 0x24 && m_iPanelType == -1)
    {
        // Forget the last command so that a second, duplicate rejection for the same
        // DL_START cannot be counted twice.
        m_lastSentCommand.size = 0;
        // Clear download mode so the retry does not log "Already in Download Mode?"
        m_bDownloadMode = false;
        this->clearQueue();

        if(m_iDlCodeRetry == 0)
        {
            // The FIRST DL_START of a connection is commonly refused even when the code
            // is correct - the panel is not ready to start a download this soon after
            // DL_EXIT.  This is long standing behaviour, not a wrong code, so retry the
            // same code once before blaming it.  Without this the ladder advances on a
            // refusal that says nothing about the code, and a panel whose code really is
            // 5650 ends up paired on whichever entry happened to come next.
            m_iDlCodeRetry++;
            DEBUG(LOG_INFO,"DL_START refused, retrying the same download code %04X", getDownloadCode());
            this->sendCommand(Pmax_DL_START);
        }
        else
        {
            // Refused twice with the same code - now it is fair to call it wrong.
            m_iDlCodeRetry = 0;
            if(advanceDownloadCode())
            {
                this->sendCommand(Pmax_DL_START);
            }
        }
        return;
    }

    DEBUG(LOG_INFO,"Access denied (not a download-code failure - check your user PIN)");
}

// MERGED: ACK-and-ignore handler for packets received but not specifically handled.
// Used as a catch-all for A5 subtypes that the PowerMaster sends unsolicited (e.g. 0x01, 0x05).
void PowerMaxAlarm::OnUnhandledMessage(const PlinkBuffer* Buff)
{
    this->sendCommand(Pmax_ACK);
    DEBUG(LOG_INFO,"OnUnhandledMessage: ACKed 0x%02X subtype=0x%02X (size=%d)",
          Buff->buffer[0], Buff->size > 2 ? Buff->buffer[2] : 0, Buff->size);
}

void PowerMaxAlarm::OnEventLog(const PlinkBuffer* Buff)
{
    this->sendCommand(Pmax_ACK);
    const unsigned char zoneId = Buff->buffer[9];
    const char* tpzone = this->getZoneName(zoneId);
    char logline[MAX_BUFFER_SIZE] = "";
    sprintf(logline,"event number:%d/%d at %d:%d:%d %d/%d/%d %s:%s",
            Buff->buffer[2],Buff->buffer[1],
            Buff->buffer[5],Buff->buffer[4],Buff->buffer[3],
            Buff->buffer[6],Buff->buffer[7],2000+Buff->buffer[8],
            tpzone, GetStrPmaxLogEvents(Buff->buffer[10]));
    DEBUG(LOG_NOTICE,"event log:%s",logline);
}

void PowerMaxAlarm::OnStatusUpdate(const PlinkBuffer* Buff)
{
    this->sendCommand(Pmax_ACK);
    DEBUG(LOG_INFO,"pmax status update");
}

// 0xA7 – armed/disarmed/alarm
//
// buffer[1] is a message count. Panels normally send 1 to 4 events in one A7, but
// they also send a count of 0xFF which is a completely different layout.
//
// On a panel with partitions that 0xFF form cannot be trusted: the HA integration
// tried decoding it and got armed/disarmed events that were never commanded, so it
// now ignores those messages entirely when partitions are in use.  We saw the same
// thing here - a PowerMaster 30 sending "A7 FF 00 00 61 ..." which we were logging
// as an "Installer Programming" event, filling the event history.
void PowerMaxAlarm::OnStatusChange(const PlinkBuffer* Buff)
{
    this->sendCommand(Pmax_ACK);

    const unsigned char msgCnt = Buff->buffer[1];

    if(msgCnt == 0xFF && this->m_bPartitionsEnabled)
    {
        DEBUG(LOG_INFO,"A7 with count 0xFF on a partitioned panel - not processed (unreliable)");
        return;
    }

    if(msgCnt > 4 && msgCnt != 0xFF)
    {
        DEBUG(LOG_WARNING,"A7 claims %d events which is too many to be valid - not processed", msgCnt);
        return;
    }

    DEBUG(LOG_INFO,"PmaxStatusChange: '%s' by '%s'(0x%X)",
          GetStrPmaxLogEvents(Buff->buffer[4]),
          GetStrPmaxEventSource(Buff->buffer[3]), Buff->buffer[3]);

    switch(Buff->buffer[4])
    {
    case 0x51: case 0x53:
        this->stat = SS_Armed_Home;
        memset(alarmTrippedZones,0,sizeof(alarmTrippedZones));
        OnSytemArmed(Buff->buffer[4],GetStrPmaxLogEvents(Buff->buffer[4]),
                     Buff->buffer[3],GetStrPmaxEventSource(Buff->buffer[3]));
        break;
    case 0x52: case 0x54:
        this->stat = SS_Armed_Away;
        memset(alarmTrippedZones,0,sizeof(alarmTrippedZones));
        OnSytemArmed(Buff->buffer[4],GetStrPmaxLogEvents(Buff->buffer[4]),
                     Buff->buffer[3],GetStrPmaxEventSource(Buff->buffer[3]));
        break;
    case 0x55:
        this->stat = SS_Disarm;
        OnSytemDisarmed(Buff->buffer[3],GetStrPmaxEventSource(Buff->buffer[3]));
        break;
    case 0x1B:
        this->alarmState = 0;
        OnAlarmCancelled(Buff->buffer[3],GetStrPmaxEventSource(Buff->buffer[3]));
        break;
    case 0x1C:
        if(this->alarmState != 0)
        {
            this->alarmState = 0;
            OnAlarmCancelled(Buff->buffer[3],GetStrPmaxEventSource(Buff->buffer[3]));
        }
        break;
    }

    if(Buff->buffer[4] > 0 && Buff->buffer[4] <= 9)
    {
        this->alarmState = Buff->buffer[4];
        OnAlarmStarted(Buff->buffer[4],GetStrPmaxLogEvents(Buff->buffer[4]),
                       Buff->buffer[3],GetStrPmaxEventSource(Buff->buffer[3]));
    }
}

// 0xA5/xx/04 – panel state + zone event
void PowerMaxAlarm::OnStatusUpdatePanel(const PlinkBuffer* Buff)
{
    this->sendCommand(Pmax_ACK);
    this->stat  = (SystemStatus)Buff->buffer[3];
    this->flags = Buff->buffer[4];

    char tpbuff[MAX_BUFFER_SIZE] = "";
    sprintf(tpbuff,"System status: %s (%d), Flags :",
            GetStrPmaxSystemStatus(this->stat), this->stat);
    for(int i=0;i<8;i++)
    {
        if(this->flags & 1<<i)
        {
            os_strncat_s(tpbuff,MAX_BUFFER_SIZE," ");
            os_strncat_s(tpbuff,MAX_BUFFER_SIZE,GetStrSystemStateFlags(i));
        }
    }
    DEBUG(LOG_INFO,"%s",tpbuff);

    if(this->isZoneEvent())
    {
        const unsigned char zoneId   = Buff->buffer[5];
        ZoneEvent eventType          = (ZoneEvent)Buff->buffer[6];
        if(zoneId < MAX_ZONE_COUNT)
        {
            DEBUG(LOG_INFO,"Zone-%d-event, (%s) %s",zoneId,
                  this->getZoneName(zoneId),GetStrPmaxZoneEventTypes(Buff->buffer[6]));
            this->zone[zoneId].lastEvent     = eventType;
            this->zone[zoneId].lastEventTime = os_getCurrentTimeSec();

            switch(eventType)
            {
            case ZE_NotActive:  this->zone[zoneId].stat.active   = false; break;
            case ZE_Open:       this->zone[zoneId].stat.doorOpen  = true;  break;
            case ZE_Closed:     this->zone[zoneId].stat.doorOpen  = false; break;
            case ZE_LowBattery:
            case ZE_SirenLowBattery:
                this->zone[zoneId].stat.lowBattery = true;  break;
            case ZE_TamperAlarm:
            case ZE_TamperOpen:
            case ZE_SirenTamper:
                this->zone[zoneId].stat.tamper = true;  break;
            case ZE_TamperRestore:
            case ZE_SirenTamperRestore:
                this->zone[zoneId].stat.tamper = false; break;
            case ZE_Violated:
                if(this->isAlarmEvent() &&
                  (this->stat == SS_Armed_Away || this->stat == SS_Armed_Home))
                {
                    if(this->zone[zoneId].enrolled && !this->zone[zoneId].stat.bypased)
                    {
                        for(int ix=0; ix<MAX_ZONE_COUNT; ix++)
                        {
                            if(this->alarmTrippedZones[ix] == 0)
                            { this->alarmTrippedZones[ix] = zoneId; break; }
                        }
                    }
                }
                break;
            default: break;
            }
        }
    }
}

void PowerMaxAlarm::OnStatusUpdateZoneBat(const PlinkBuffer* Buff)
{
    this->sendCommand(Pmax_ACK);
    DEBUG(LOG_INFO,"Status Update : Zone state/Battery");
    // A5 packets carry exactly 4 bytes (32 zones) per bitmask field.
    // Looping past 32 would overflow into the adjacent bitmask and create
    // false zone data. Cap at 32 regardless of MAX_ZONE_COUNT.
    const unsigned char* ZoneBuffer = Buff->buffer+3;
    for(int i=1;i<=32;i++)
    {
        int byte=(i-1)/8, offset=(i-1)%8;
        this->zone[i].stat.doorOpen  = (ZoneBuffer[byte] & 1<<offset) != 0;
    }
    ZoneBuffer = Buff->buffer+7;
    for(int i=1;i<=32;i++)
    {
        int byte=(i-1)/8, offset=(i-1)%8;
        this->zone[i].stat.lowBattery = (ZoneBuffer[byte] & 1<<offset) != 0;
    }
}

void PowerMaxAlarm::OnStatusUpdateZoneTamper(const PlinkBuffer* Buff)
{
    this->sendCommand(Pmax_ACK);
    DEBUG(LOG_INFO,"Status Update : Zone active/tampered");
    // A5 packets carry exactly 4 bytes (32 zones) per bitmask field.
    // Cap at 32 to avoid overflowing into the adjacent bitmask.
    const unsigned char* ZoneBuffer = Buff->buffer+3;
    for(int i=1;i<=32;i++)
    {
        int byte=(i-1)/8, offset=(i-1)%8;
        this->zone[i].stat.active = !(ZoneBuffer[byte] & 1<<offset);
    }
    ZoneBuffer = Buff->buffer+7;
    for(int i=1;i<=32;i++)
    {
        int byte=(i-1)/8, offset=(i-1)%8;
        this->zone[i].stat.tamper = (ZoneBuffer[byte] & 1<<offset) != 0;
    }
}

void PowerMaxAlarm::OnStatusUpdateZoneBypassed(const PlinkBuffer* Buff)
{
    this->sendCommand(Pmax_ACK);
    DEBUG(LOG_INFO,"Status Update : Zone Enrolled/Bypassed");
    // A5 packets carry exactly 4 bytes (32 zones) per bitmask field.
    // buffer[3..6] = enrollment bitmask (zones 1-32)
    // buffer[7..10] = bypass bitmask    (zones 1-32)
    // Looping to MAX_ZONE_COUNT=64 would read 8 bytes and overflow the
    // enrollment loop into the bypass bytes, creating phantom enrolled zones.
    // Always cap at 32 for A5 packets.
    const unsigned char* ZoneBuffer = Buff->buffer+3;
    for(int i=1;i<=32;i++)
    {
        int byte=(i-1)/8, offset=(i-1)%8;
        this->zone[i].enrolled = (ZoneBuffer[byte] & 1<<offset) != 0;
        // Guard against NULL sensorType/sensorMake. A5 0x06 sets the enrolled
        // bitmask but B0 0x1F (DEVICE_TYPES) may not have arrived yet, leaving
        // these pointers NULL. getZoneSensorType() returns zone[i].sensorType
        // directly for enrolled zones so a NULL would crash strncpy in the .ino.
        if(this->zone[i].sensorType == NULL)
            this->zone[i].sensorType = "Unknown";
        if(this->zone[i].sensorMake == NULL)
            this->zone[i].sensorMake = "Visonic Unknown";
    }
    ZoneBuffer = Buff->buffer+7;
    for(int i=1;i<=32;i++)
    {
        int byte=(i-1)/8, offset=(i-1)%8;
        this->zone[i].stat.bypased = (ZoneBuffer[byte] & 1<<offset) != 0;
    }
}

// ############################################################
// MERGED: OnPowerMasterMessage – handles incoming 0xB0 packets
//
// PowerMaster panels send all real-time data via B0 messages.
//
// CAREFUL WITH OFFSETS.  Buff->buffer here still holds the leading 0xB0 (the
// 0x0D preamble and the trailing CRC/0x0A have been stripped by deFormatBuffer).
// The HA integration strips the 0xB0 as well before indexing, so every offset
// quoted in pyvisonic.py is ONE LOWER than the offset to use here.
//
//   [0] 0xB0
//   [1] msgType  (0x03 = data push, 0x02 = paged sub-message, 0x01 = response)
//   [2] subType  (0x18 open/close, 0x19 bypass, 0x1D enrol, 0x24 panel state, ...)
//   [3] msgLen   (Buff->size always == msgLen + 5)
//   --- then, for "chunky" messages, one or more chunks: ---
//         <seq>      0xFF = single/complete, else a page number
//         <datasize> bits per entry: 1 = bitmask, 8 = one byte per device
//         <index>    device class: 2 = SIRENS, 3 = ZONES, 255 = MIXED
//         <length>   number of data bytes in THIS chunk
//         <length bytes of data>
//   --- and finally: ---
//   [size-2] counter
//   [size-1] 0x43 (POWERLINK_TERMINAL)
//
// A single message can carry several chunks for different device classes, so
// always pull the one you want out with findB0Chunk() rather than assuming the
// payload is flat and starts at a fixed offset.
//
// Non-chunky messages (e.g. 0x0F PANEL_STATE_2) have fixed formats instead.
// ############################################################
void PowerMaxAlarm::OnPowerMasterMessage(const PlinkBuffer* Buff)
{
    this->sendCommand(Pmax_ACK);

    if(Buff->size < 4)
    {
        DEBUG(LOG_WARNING,"B0 message too short: %d bytes", Buff->size);
        return;
    }

    // buffer[0] = 0xB0 (dispatch table verified this)
    // buffer[1] = B0 msgType:  0x03 = unsolicited data push, 0x01 = keepalive/response
    // buffer[2] = subType:     0x18 open/close, 0x19 bypass, 0x1D enrol, 0x24 panel, 0x0F panel, 0x6A keepalive
    // buffer[3] = msgLen:      payload byte count after this byte
    const unsigned char b0MsgType = Buff->buffer[1];
    const unsigned char subType   = Buff->buffer[2];
    const unsigned char msgLen    = Buff->buffer[3];

    DEBUG(LOG_INFO,"PowerMaster B0 message: b0MsgType=0x%02X subType=0x%02X msgLen=%d size=%d",
          b0MsgType, subType, msgLen, Buff->size);

    switch(subType)
    {
    // -------------------------------------------------------
    // 0x06 – INVALID_COMMAND response.
    // Panel sends this when it doesn't recognise a request.
    // Silently ignore – already ACKed at function entry.
    // -------------------------------------------------------
    case 0x06:
        DEBUG(LOG_INFO,"B0 INVALID_COMMAND (0x06) from panel – ignoring");
        break;

    // -------------------------------------------------------
    // 0x39 – ASK_ME_1: panel is saying it has fresh data for
    // the listed subtypes.  Extract the subtype list from the
    // chunky payload and request them all in one B0 packet.
    // The subtype list arrives in a MIXED-index chunk.
    //
    // MERGED: On the first B0 0x39 after any (re)connection we also piggyback
    // the zone-init subtypes (0x1D enrol, 0x1F device types, 0x21 zone names,
    // 0x2D zone types) so that zone names/types are populated even when the ESP
    // crashed before the OnStop-queued zone-init request was sent.
    // -------------------------------------------------------
    case 0x39:
        {
            int panelCount = 0;
            const unsigned char* askData = findB0Chunk(Buff, 0xFF /* MIXED */, &panelCount, NULL);
            if(askData != NULL)
            {
                if(panelCount > 0 && panelCount <= 10)
                {
                    if(!m_bPmZoneDataRequested)
                    {
                        // Build merged list: zone-init subtypes first, then panel's
                        // requested subtypes (skip duplicates), capped at 10 total.
                        const unsigned char zoneInit[] = {0x1D, 0x1F, 0x21, 0x2D};
                        const int zoneInitCount = 4;
                        unsigned char merged[10];
                        int mergedCount = 0;
                        for(int i = 0; i < zoneInitCount && mergedCount < 10; i++)
                            merged[mergedCount++] = zoneInit[i];
                        for(int i = 0; i < panelCount && mergedCount < 10; i++)
                        {
                            unsigned char sub = askData[i];
                            bool dup = false;
                            for(int j = 0; j < mergedCount; j++)
                                if(merged[j] == sub) { dup = true; break; }
                            if(!dup) merged[mergedCount++] = sub;
                        }
                        DEBUG(LOG_INFO,"B0 ASK_ME_1 (0x39): first exchange, requesting %d subtypes (zone init + panel)", mergedCount);
                        sendPmB0Request(merged, mergedCount);
                        m_bPmZoneDataRequested = true;
                    }
                    else
                    {
                        DEBUG(LOG_INFO,"B0 ASK_ME_1 (0x39): panel wants us to request %d subtype(s)", panelCount);
                        sendPmB0Request(askData, panelCount);
                    }
                }
                else
                {
                    DEBUG(LOG_INFO,"B0 ASK_ME_1 (0x39): count=%d out of range, ignoring", panelCount);
                }
            }
            else
            {
                DEBUG(LOG_INFO,"B0 ASK_ME_1 (0x39): no MIXED chunk, ignoring");
            }
        }
        break;

    // -------------------------------------------------------
    // 0x6A – Keep-alive response (B0 01 6A 00 43).
    // Panel echoes our PM_KEEPALIVE; reset the watchdog timer.
    // -------------------------------------------------------
    case 0x6A:
        DEBUG(LOG_INFO,"B0 Keep-alive response received");
        this->startKeepAliveTimer();
        break;

    // -------------------------------------------------------
    // 0x0F – PANEL_STATE_2 (non-chunky, msgLen == 11 or 15).
    // Compact panel-state pushed unsolicited by the panel.
    // HA reference packet (msgLen=15, 3 partitions), indexed as we hold it here
    // (i.e. including the leading 0xB0, which HA strips before indexing):
    //   idx: 0  1  2  3   4  5  6  7  8  9 10  11 12 13 14 15 16 17 18  19
    //        B0 03 0F 0F  07 08 0F 00 00 00 43 03 00 87 00 87 00 07 24  43
    //                                            ^  ^  ^
    //                                            |  |  +-- sysFlags  partition 1
    //                                            |  +----- sysStatus partition 1
    //                                            +-------- partition count
    // HA reads these as data[10]/data[11]/data[12]; our buffer keeps the 0xB0 at
    // index 0, so every offset is one higher.  This used to read [11]/[12], which
    // decoded the partition count as the status (3 -> "Entry Delay") on every
    // single 0x0F the panel pushed.
    // -------------------------------------------------------
    case 0x0F:
        if(Buff->size >= 14)
        {
            const unsigned char partCount = Buff->buffer[11];
            this->stat  = (SystemStatus)(Buff->buffer[12] & 0x0F);
            this->flags = Buff->buffer[13];
            DEBUG(LOG_INFO,"B0 PANEL_STATE_2: partCount=%d status=%s flags=0x%02X",
                  partCount, GetStrPmaxSystemStatus(this->stat), this->flags);
        }
        else
        {
            DEBUG(LOG_WARNING,"B0 PANEL_STATE_2 too short (size=%d), ignoring", Buff->size);
        }
        break;

    // -------------------------------------------------------
    // 0x24 – PANEL_STATE_1 (chunky, MIXED index).
    // Contains panel arm status + partition data + timestamp.
    // Within the chunk data, HA recognises three lengths:
    //   len 21: [8..13] time, [16] partition count (==1), [17] status, [18] flags
    //   len 28: [8..13] time, no partition-count byte, [16] status, [17] flags
    //   len 29: [8..13] time, [16] partition count,     [17] status, [18] flags
    // We only track the first partition.
    // -------------------------------------------------------
    case 0x24:
        {
            int dataBytes = 0;
            const unsigned char* data = findB0Chunk(Buff, 0xFF /* MIXED */, &dataBytes, NULL);
            if(data == NULL) { DEBUG(LOG_INFO,"B0 PANEL_STATE_1: no MIXED chunk"); break; }

            int statusOff;
            if(dataBytes == 28)      statusOff = 16;   // no partition-count byte in this variant
            else if(dataBytes >= 21) statusOff = 17;
            else
            {
                DEBUG(LOG_INFO,"B0 PANEL_STATE_1: unhandled chunk length %d, ignoring", dataBytes);
                break;
            }

            if(statusOff + 1 < dataBytes)
            {
                const unsigned char partCount = (dataBytes == 28) ? 3 : data[16];
                const unsigned char sysStatus = data[statusOff];
                const unsigned char sysFlags  = data[statusOff + 1];
                this->stat  = (SystemStatus)(sysStatus & 0x0F);
                this->flags = sysFlags & 0x7F;
                DEBUG(LOG_INFO,"B0 PANEL_STATE_1: len=%d partCount=%d status=%s flags=0x%02X",
                      dataBytes, partCount, GetStrPmaxSystemStatus(this->stat), this->flags);
            }
        }
        break;

    // -------------------------------------------------------
    // 0x18 – ZONE_OPENCLOSE (chunky, bitmask per zone).
    // Payload data is a bit-array: bit N=1 means zone N+1 is OPEN.
    // The bitmask covers 64 zones (8 bytes = 64 bits).
    // -------------------------------------------------------
    case 0x18:
        {
            int dataBytes = 0;
            const unsigned char* data = findB0Chunk(Buff, B0_INDEX_ZONES, &dataBytes, NULL);
            if(data == NULL) { DEBUG(LOG_INFO,"B0 ZONE_OPENCLOSE: no ZONES chunk"); break; }
            if(dataBytes > 8) dataBytes = 8;  // max 64 zones
            DEBUG(LOG_INFO,"B0 ZONE_OPENCLOSE: updating %d zone bytes", dataBytes);
            for(int b=0; b<dataBytes; b++)
            {
                unsigned char val = data[b];
                for(int bit=0; bit<8; bit++)
                {
                    int zoneId = b*8 + bit + 1;
                    if(zoneId < MAX_ZONE_COUNT)
                        this->zone[zoneId].stat.doorOpen = (val & (1<<bit)) != 0;
                }
            }
        }
        break;

    // -------------------------------------------------------
    // 0x19 – ZONE_BYPASS (chunky, bitmask per zone).
    // Payload data is a bit-array: bit N=1 means zone N+1 is BYPASSED.
    // -------------------------------------------------------
    case 0x19:
        {
            int dataBytes = 0;
            const unsigned char* data = findB0Chunk(Buff, B0_INDEX_ZONES, &dataBytes, NULL);
            if(data == NULL) { DEBUG(LOG_INFO,"B0 ZONE_BYPASS: no ZONES chunk"); break; }
            if(dataBytes > 8) dataBytes = 8;
            DEBUG(LOG_INFO,"B0 ZONE_BYPASS: updating %d zone bytes", dataBytes);
            for(int b=0; b<dataBytes; b++)
            {
                unsigned char val = data[b];
                for(int bit=0; bit<8; bit++)
                {
                    int zoneId = b*8 + bit + 1;
                    if(zoneId < MAX_ZONE_COUNT)
                        this->zone[zoneId].stat.bypased = (val & (1<<bit)) != 0;
                }
            }
        }
        break;

    // -------------------------------------------------------
    // 0x1D – SENSOR_ENROL (chunky, bitmask per zone).
    // Payload data is a bit-array: bit N=1 means zone N+1 is ENROLLED.
    // -------------------------------------------------------
    case 0x1D:
        {
            // NOTE: this message also carries SIRENS / KEYFOBS / KEYPADS chunks.
            // Only the ZONES chunk is zone-enrolment data.
            int dataBytes = 0;
            const unsigned char* data = findB0Chunk(Buff, B0_INDEX_ZONES, &dataBytes, NULL);
            if(data == NULL) { DEBUG(LOG_INFO,"B0 SENSOR_ENROL: no ZONES chunk"); break; }
            if(dataBytes > 8) dataBytes = 8;
            DEBUG(LOG_INFO,"B0 SENSOR_ENROL: updating %d zone bytes", dataBytes);
            for(int b=0; b<dataBytes; b++)
            {
                unsigned char val = data[b];
                for(int bit=0; bit<8; bit++)
                {
                    int zoneId = b*8 + bit + 1;
                    if(zoneId < MAX_ZONE_COUNT)
                    {
                        this->zone[zoneId].enrolled = (val & (1<<bit)) != 0;
                        // Ensure sensorType/sensorMake are never NULL.
                        // B0 0x1F (DEVICE_TYPES) populates them later; until then
                        // any call to getZoneSensorType() must not return a NULL pointer.
                        if(this->zone[zoneId].sensorType == NULL)
                            this->zone[zoneId].sensorType = "Unknown";
                        if(this->zone[zoneId].sensorMake == NULL)
                            this->zone[zoneId].sensorMake = "Visonic Unknown";
                    }
                }
            }
        }
        break;

    // -------------------------------------------------------
    // 0x1F – DEVICE_TYPES (chunky, one byte per zone = sensor device-type ID).
    // Each enrolled zone's byte identifies the hardware sensor model.
    // Look up in PmZoneMasterTypes to get sensorType / sensorMake strings.
    // -------------------------------------------------------
    case 0x1F:
        {
            // This message carries a ZONES chunk AND a SIRENS chunk - reading it as
            // one flat block used to write siren device types over the high zones.
            int dataBytes = 0;
            const unsigned char* data = findB0Chunk(Buff, B0_INDEX_ZONES, &dataBytes, NULL);
            if(data == NULL) { DEBUG(LOG_INFO,"B0 DEVICE_TYPES: no ZONES chunk"); break; }
            if(dataBytes > MAX_ZONE_COUNT) dataBytes = MAX_ZONE_COUNT;
            DEBUG(LOG_INFO,"B0 DEVICE_TYPES: updating %d zone bytes", dataBytes);
            for(int i = 0; i < dataBytes; i++)
            {
                int zoneId = i + 1;
                if(zoneId < MAX_ZONE_COUNT)
                {
                    unsigned char devType = data[i];
                    if(devType != 0x00)
                    {
                        zone[zoneId].sensorId = devType;
                        lookupPmasterSensor(devType, &zone[zoneId].sensorType, &zone[zoneId].sensorMake);
                    }
                }
            }
        }
        break;

    // -------------------------------------------------------
    // 0x21 – ZONE_NAMES (chunky, one byte per zone = name-string index).
    // Each byte is an index into PmZoneNameTable[] (matches HA pmZoneName[]).
    // -------------------------------------------------------
    case 0x21:
        {
            int dataBytes = 0;
            const unsigned char* data = findB0Chunk(Buff, B0_INDEX_ZONES, &dataBytes, NULL);
            if(data == NULL) { DEBUG(LOG_INFO,"B0 ZONE_NAMES: no ZONES chunk"); break; }
            if(dataBytes > MAX_ZONE_COUNT) dataBytes = MAX_ZONE_COUNT;
            DEBUG(LOG_INFO,"B0 ZONE_NAMES: updating %d zone name bytes", dataBytes);
            for(int i = 0; i < dataBytes; i++)
            {
                int zoneId = i + 1;
                if(zoneId < MAX_ZONE_COUNT)
                {
                    unsigned char nameIdx = data[i] & 0x1F;
                    if(nameIdx < PmZoneNameTableSize && PmZoneNameTable[nameIdx] != NULL)
                        strncpy(zone[zoneId].name, PmZoneNameTable[nameIdx], sizeof(zone[zoneId].name)-1);
                    else
                        snprintf(zone[zoneId].name, sizeof(zone[zoneId].name), "Zone %d", zoneId);
                    zone[zoneId].name[sizeof(zone[zoneId].name)-1] = '\0';
                }
            }
        }
        break;

    // -------------------------------------------------------
    // 0x2D – ZONE_TYPES (chunky, one byte per zone = zone-type index).
    // Each byte is an index into the zone-type string table (same as EPROM path).
    // -------------------------------------------------------
    case 0x2D:
        {
            int dataBytes = 0;
            const unsigned char* data = findB0Chunk(Buff, B0_INDEX_ZONES, &dataBytes, NULL);
            if(data == NULL) { DEBUG(LOG_INFO,"B0 ZONE_TYPES: no ZONES chunk"); break; }
            if(dataBytes > MAX_ZONE_COUNT) dataBytes = MAX_ZONE_COUNT;
            DEBUG(LOG_INFO,"B0 ZONE_TYPES: updating %d zone type bytes", dataBytes);
            for(int i = 0; i < dataBytes; i++)
            {
                int zoneId = i + 1;
                if(zoneId < MAX_ZONE_COUNT)
                {
                    zone[zoneId].zoneType    = data[i];
                    zone[zoneId].zoneTypeStr = GetStrPmaxZoneTypes(zone[zoneId].zoneType);
                }
            }
        }
        break;

    // -------------------------------------------------------
    // All other subtypes – log and ignore.
    // -------------------------------------------------------
    default:
        DEBUG(LOG_INFO,"B0 b0MsgType=0x%02X subType=0x%02X not handled (size=%d). Add handler if needed.",
              b0MsgType, subType, Buff->size);
        break;
    }
}

// ############################################################
// MERGED: sendPmB0Request
// Build and queue a B0 data-request packet asking the panel to push back
// one or more B0 subtypes.  Packet format (from HA _create_B0_Data_Request):
//   B0 01 17 <total_len> 01 FF 08 FF <count> <subtype_0> … <subtype_N-1> 43
// where total_len = count + 5.
// Maximum 10 subtypes per call (fits in one packet with plenty of margin).
// ############################################################
void PowerMaxAlarm::sendPmB0Request(const unsigned char* subtypes, int count)
{
    if(count <= 0 || count > 10)
    {
        DEBUG(LOG_WARNING,"sendPmB0Request: bad count %d", count);
        return;
    }
    // Fixed prefix: B0 01 17 <len> 01 FF 08 FF <count>
    const int prefixLen = 9;
    const int totalLen  = prefixLen + count + 1;  // +1 for terminator 0x43
    unsigned char buff[32] = {0};
    buff[0] = 0xB0;
    buff[1] = 0x01;
    buff[2] = 0x17;
    buff[3] = (unsigned char)(count + 5);  // total_len field = count + 5
    buff[4] = 0x01;
    buff[5] = 0xFF;
    buff[6] = 0x08;
    buff[7] = 0xFF;
    buff[8] = (unsigned char)count;
    for(int i = 0; i < count; i++)
        buff[9 + i] = subtypes[i];
    buff[9 + count] = 0x43;  // POWERLINK_TERMINAL
    queueCommand(buff, totalLen, "PM_B0_REQ");
    DEBUG(LOG_INFO,"sendPmB0Request: queued request for %d subtypes", count);
}

// ############################################################
// sendNextCommand
// MERGED: PowerMaster uses B0 keep-alive at 15s, PowerMax uses AB ping at 30s.
// ############################################################
void PowerMaxAlarm::sendNextCommand()
{
    if(m_bDownloadMode == false)
    {
        // Keep-alive interval: PowerMaster 15 s, PowerMax 30 s.
        unsigned long keepAliveInterval = m_bPowerMaster ? 15 : 30;

        if(os_getCurrentTimeSec() - m_ulLastPing > keepAliveInterval)
        {
            m_ulLastPing = os_getCurrentTimeSec();
            // Which keep-alive to send depends on whether the panel understands 0xAB,
            // NOT on whether it is a PowerMaster.  PowerMaster 10/30/33 all speak AB and
            // answer the B0 01 6A keep-alive with B0 03 06 (INVALID_COMMAND) - which is
            // what the telnet captures showed.  Only the 360 / 360R need the B0 form.
            if(m_bAbSupported)
                sendCommand(Pmax_PING);
            else
                sendCommand(Pmax_PM_KEEPALIVE);
            return;
        }
    }

    if(m_sendQueue.isEmpty()) return;

    os_usleep(50*1000);  // 50 ms inter-message gap

    PmQueueItem item = m_sendQueue.pop();
    sendBuffer(item.buffer, item.bufferLen);
}

// ############################################################
// Utility helpers (unchanged from original)
// ############################################################
bool PowerMaxAlarm::queueCommand(const unsigned char* buffer, int bufferLen,
                                  const char* description, unsigned char expectedRepply,
                                  const char* options)
{
    if(m_sendQueue.isFull())
    {
        DEBUG(LOG_CRIT,"Send queue is full, dropping packet: %s", description);
        return false;
    }
    if(bufferLen > MAX_SEND_BUFFER_SIZE)
    {
        DEBUG(LOG_CRIT,"Buffer to send too long: %d", bufferLen);
        return false;
    }
    PmQueueItem item;
    memcpy(item.buffer, buffer, bufferLen);
    item.bufferLen      = bufferLen;
    item.description    = description;
    item.expectedRepply = expectedRepply;
    item.options        = options;
    m_sendQueue.push(item);
    return true;
}

static unsigned char calculChecksum(const unsigned char* data, int dataSize)
{
    unsigned short checksum = 0xFFFF;
    for(int i=0;i<dataSize;i++) checksum -= data[i];
    checksum %= 0xFF;
    return (unsigned char)checksum;
}

// PowerMaster panels require a different checksum formula for AB (0xAB) commands.
// Standard formula : 0xFF - (sum % 0xFF), 0xFF → 0x00
// Alternate formula : 256  - (sum % 255), 256  → 1
// The alternate result is always exactly 1 more than the standard result.
// Source: HA visonic integration – _calculateCRCAlt() in pyhelper.py.
static unsigned char calculChecksumAlt(const unsigned char* data, int dataSize)
{
    unsigned int sum = 0;
    for(int i=0;i<dataSize;i++) sum += data[i];
    unsigned int checksum = 256 - (sum % 255);
    if(checksum == 256) checksum = 1;
    return (unsigned char)checksum;
}

bool PowerMaxAlarm::sendBuffer(const unsigned char* data, int bufferSize)
{
    DEBUG(LOG_DEBUG,"Sending buffer to serial TTY");
    if(bufferSize >= MAX_BUFFER_SIZE-2 || bufferSize > MAX_SEND_BUFFER_SIZE)
    {
        DEBUG(LOG_ERR,"Too long buffer: %d", bufferSize);
        return false;
    }
    memcpy(m_lastSentCommand.buffer, data, bufferSize);
    m_lastSentCommand.size = bufferSize;

    PlinkBuffer writeBuffer;
    writeBuffer.buffer[0] = 0x0D;
    for(int i=0;i<bufferSize;i++) writeBuffer.buffer[i+1] = data[i];
    // PowerMaster panels require alternate CRC for AB (0xAB) commands.
    // All other commands (A1 arm/disarm, B0, etc.) use the standard CRC.
    if(m_bPowerMaster && bufferSize > 0 && data[0] == 0xAB)
        writeBuffer.buffer[bufferSize+1] = calculChecksumAlt(data, bufferSize);
    else
        writeBuffer.buffer[bufferSize+1] = calculChecksum(data, bufferSize);
    writeBuffer.buffer[bufferSize+2] = 0x0A;
    writeBuffer.size = bufferSize+3;

    const int bytesWritten = os_pmComPortWrite(writeBuffer.buffer, bufferSize+3);
    if(bytesWritten == bufferSize+3)
    {
        DEBUG(LOG_DEBUG,"serial write OK");
        return true;
    }
    DEBUG(LOG_ERR,"serial write failed, bytes: %d expected: %d", bytesWritten, bufferSize+3);
    return false;
}

void PowerMaxAlarm::sendBuffer(struct PlinkBuffer* Buff)
{
    sendBuffer(Buff->buffer, Buff->size);
}

static bool deFormatBuffer(struct PlinkBuffer* Buff, bool ignoreErrors)
{
    const unsigned char checksum = Buff->buffer[Buff->size-2];
    for(int i=0;i<Buff->size;i++) Buff->buffer[i] = Buff->buffer[i+1];
    Buff->size -= 3;
    // Try standard CRC first (used by most panel messages).
    const unsigned char checkedChecksum = calculChecksum(Buff->buffer, Buff->size);
    if(checksum == checkedChecksum)
    {
        DEBUG(LOG_DEBUG,"checksum OK");
        return true;
    }
    // PowerMaster panels send AB (0xAB) messages with the alternate CRC formula.
    // Try that before declaring the packet malformed, matching HA's _validatePDU behaviour.
    const unsigned char altChecksum = calculChecksumAlt(Buff->buffer, Buff->size);
    if(checksum == altChecksum)
    {
        DEBUG(LOG_DEBUG,"checksum OK (alt)");
        return true;
    }
    if(!ignoreErrors)
        DEBUG(LOG_ERR,"checksum NOK std:%02X alt:%02X pkt:%02X", checkedChecksum, altChecksum, checksum);
    return false;
}

static bool findCommand(const PlinkBuffer* Buff, const PlinkCommand* BuffCommand)
{
    if(BuffCommand->size < 0)
    {
        if(Buff->size < (-BuffCommand->size)) return false;
        for(int i=0;i<(-BuffCommand->size);i++)
            if(Buff->buffer[i] != BuffCommand->buffer[i] && BuffCommand->buffer[i] != 0xFF) return false;
    }
    else
    {
        if(Buff->size != BuffCommand->size) return false;
        for(int i=0;i<Buff->size;i++)
            if(Buff->buffer[i] != BuffCommand->buffer[i] && BuffCommand->buffer[i] != 0xFF) return false;
    }
    return true;
}

bool PowerMaxAlarm::isBufferOK(const PlinkBuffer* commandBuffer)
{
    const int old = log_console_setlogmask(LOG_ALERT);
    PlinkBuffer tmp;
    memcpy(&tmp, commandBuffer, sizeof(PlinkBuffer));
    bool ok = deFormatBuffer(&tmp, true);
    log_console_setlogmask(old);
    return ok;
}

PmAckType PowerMaxAlarm::calculateAckType(const unsigned char* buf, int len)
{
    if(len > 1)
    {
        if(buf[0] >= 0x80 || (buf[0] < 0x10 && buf[len-1] == 0x43))
            return ACK_2;
    }
    return ACK_1;
}

void PowerMaxAlarm::handlePacket(PlinkBuffer* commandBuffer)
{
    m_ackTypeForLastMsg = ACK_1;

    if(deFormatBuffer(commandBuffer, false))
    {
        DEBUG(LOG_DEBUG,"Packet received");
        lastIoTime = os_getCurrentTimeSec();
        m_ackTypeForLastMsg = calculateAckType(commandBuffer->buffer, commandBuffer->size);

        int cmd_not_recognized = 1;
        const int cmdCnt = (sizeof(PmaxCommandTable)/sizeof(PmaxCommandTable[0]));
        for(int i=0; i<cmdCnt; i++)
        {
            if(findCommand(commandBuffer, &PmaxCommandTable[i]))
            {
                DEBUG(LOG_INFO,"Command found: '%s'", PmaxCommandTable[i].description);
                (this->*PmaxCommandTable[i].action)(commandBuffer);
                cmd_not_recognized = 0;
                break;
            }
        }

        if(cmd_not_recognized == 1)
        {
            DEBUG(LOG_INFO,"Packet not recognized (0x%02X)", commandBuffer->buffer[0]);
            sendCommand(Pmax_ACK);
        }
    }
    else
    {
        DEBUG(LOG_ERR,"Packet not correctly formatted");
    }
    commandBuffer->size = 0;
    DEBUG(LOG_DEBUG,"End of packet treatment");
}

const char* PowerMaxAlarm::getZoneName(unsigned char zoneId)
{
    if(zoneId < MAX_ZONE_COUNT && zone[zoneId].enrolled)
        return zone[zoneId].name;
    return "Unknown";
}

// ############################################################
// JSON dump helpers
// ############################################################
void ConsoleOutput::write(const char* str)
{
    DEBUG_RAW(LOG_NO_FILTER,"%s",str);
}

void PowerMaxAlarm::dumpToJson(IOutput* outputStream)
{
    outputStream->write("{");
    OnDumpToJsonStarted(outputStream);
    outputStream->writeJsonTag("stat",             stat);
    outputStream->writeJsonTag("stat_str",         GetStrPmaxSystemStatus(stat));
    outputStream->writeJsonTag("lastCom",          (int)getSecondsFromLastComm());
    outputStream->writeJsonTag("panelType",        m_iPanelType);
    outputStream->writeJsonTag("panelTypeStr",     GetStrPmaxPanelType(m_iPanelType));
    outputStream->writeJsonTag("panelModelType",   m_iModelType);
    outputStream->writeJsonTag("powerMaster",      m_bPowerMaster);

    // The download code actually in use, and where it came from. Without this the only
    // way to tell a user override from a ladder position is to catch a debug line that
    // is printed before telnet debugging can realistically be switched on.
    {
        char dlbuf[8];
        sprintf(dlbuf, "%04X", getDownloadCode() & 0xFFFF);
        outputStream->writeJsonTag("downloadCode",       dlbuf);
        outputStream->writeJsonTag("downloadCodeSource", (m_iDlCodeIndex < 0) ? "override" : "ladder");
        outputStream->writeJsonTag("downloadCodeTries",  m_iDlCodeAdvances);
    }
    outputStream->writeJsonTag("alarmState",       alarmState);
    outputStream->writeJsonTag("alarmStateStr",    GetStrPmaxLogEvents(alarmState));

    outputStream->write("\"alarmTrippedZones\":[");
    for(int ix=0; ix<MAX_ZONE_COUNT; ix++)
    {
        if(alarmTrippedZones[ix] == 0) break;
        if(ix > 0) outputStream->write(",\r\n");
        char zoneId[10] = "";
        sprintf(zoneId,"%d",alarmTrippedZones[ix]);
        outputStream->write(zoneId);
    }
    outputStream->write("],\r\n");

    if(m_cfg.parsedOK)
    {
        outputStream->write("\"config\":");
        m_cfg.DumpToJson(outputStream);
        outputStream->write(",\r\n");
    }

    outputStream->writeJsonTag("flags",               flags);
    outputStream->writeJsonTag("flags_ready",         isFlagSet(0));
    outputStream->writeJsonTag("flags_alertInMemory", isFlagSet(1));
    outputStream->writeJsonTag("flags_trouble",       isFlagSet(2));
    outputStream->writeJsonTag("flags_bypasOn",       isFlagSet(3));
    outputStream->writeJsonTag("flags_last10sec",     isFlagSet(4));
    outputStream->writeJsonTag("flags_zoneEvent",     isFlagSet(5));
    outputStream->writeJsonTag("flags_armDisarmEvent",isFlagSet(6));
    outputStream->writeJsonTag("flags_alarmEvent",    isFlagSet(7));

    outputStream->write("\"enroled_zones\":[");
    int addedCnt = 0;
    for(int ix=1; ix<MAX_ZONE_COUNT; ix++)
    {
        if(zone[ix].enrolled)
        {
            if(addedCnt > 0) outputStream->write(",\r\n");
            addedCnt++;
            zone[ix].DumpToJson(outputStream);
        }
    }
    outputStream->write("]");
    outputStream->write("}");
}

void Zone::DumpToJson(IOutput* outputStream)
{
    outputStream->write("{");
    outputStream->writeJsonTag("zoneName",      name);
    outputStream->writeJsonTag("zoneType",      zoneType);
    outputStream->writeJsonTag("zoneTypeStr",   zoneTypeStr);
    outputStream->writeJsonTag("sensorId",      sensorId);
    outputStream->writeJsonTag("sensorType",    sensorType);
    outputStream->writeJsonTag("sensorMake",    sensorMake);
    outputStream->writeJsonTag("signalStrength",(int)signalStrength);
    if(lastEventTime > 0)
    {
        outputStream->writeJsonTag("lastEvent",   lastEvent);
        outputStream->writeJsonTag("lastEventAge",(int)(os_getCurrentTimeSec()-lastEventTime));
    }
    outputStream->writeJsonTag("stat_doorOpen",   stat.doorOpen);
    outputStream->writeJsonTag("stat_bypased",    stat.bypased);
    outputStream->writeJsonTag("stat_lowBattery", stat.lowBattery);
    outputStream->writeJsonTag("stat_active",     stat.active);
    outputStream->writeJsonTag("stat_tamper",     stat.tamper, false);
    outputStream->write("}");
}

void PmConfig::DumpToJson(IOutput* outputStream)
{
    outputStream->write("{");
    outputStream->writeJsonTag("master_code",             masterCode);
    outputStream->writeJsonTag("installer_code",          installerCode);
    outputStream->writeJsonTag("master_download_code",    masterDownloadCode);
    outputStream->writeJsonTag("installer_download_code", installerDownloadCode);
    outputStream->writeJsonTag("powerlink_code",          powerlinkCode);
    outputStream->write("\"user_pins\":[");
    bool first = true;
    for(int ix=0; ix<maxUserCnt; ix++)
    {
        if(strcmp(userPins[ix],"0000") != 0)
        {
            if(!first) outputStream->write(",");
            outputStream->write("\"");
            outputStream->write(userPins[ix]);
            outputStream->write("\"");
            first = false;
        }
    }
    outputStream->write("],\r\n");
    outputStream->write("\"telephone_numbers\":[");
    first = true;
    for(int ix=0; ix<4; ix++)
    {
        if(phone[ix][0] != '\0')
        {
            if(!first) outputStream->write(",");
            outputStream->write("\"");
            outputStream->write(phone[ix]);
            outputStream->write("\"");
            first = false;
        }
    }
    outputStream->write("],\r\n");
    outputStream->writeJsonTag("serial_number",  serialNumber);
    outputStream->writeJsonTag("eprom",          eprom);
    outputStream->writeJsonTag("software",       software);
    outputStream->writeJsonTag("partitionCnt",   (int)partitionCnt, false);
    outputStream->write("}");
}

int PmConfig::GetMasterPinAsHex() const
{
    return strtol(userPins[0], NULL, 16);
}

void IOutput::writeQuotedStr(const char* str)
{
    write("\"");
    write(str);
    write("\"");
}

void IOutput::writeJsonTag(const char* name, bool value, bool addComma)
{
    writeJsonTag(name, value ? "true" : "false", addComma, false);
}

void IOutput::writeJsonTag(const char* name, int value, bool addComma)
{
    char szTmp[20];
    sprintf(szTmp,"%d",value);
    writeJsonTag(name, szTmp, addComma, false);
}

void IOutput::writeJsonTag(const char* name, const char* value, bool addComma, bool quoteValue)
{
    if(value == NULL) value = "";   // guard against NULL const char* fields (e.g. unset sensorType)
    writeQuotedStr(name);
    write(":");
    if(quoteValue) writeQuotedStr(value);
    else write(value);
    if(addComma) write(",\r\n");
}
