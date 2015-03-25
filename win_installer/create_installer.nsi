; NSIS Kontalk install script

;NSIS Modern User Interface
;Multilingual Example Script

;  !include "Library.nsh"
  !include "MUI.nsh"

;--------------------------------
;Defines  
  
!define APPNAME "Kontalk Desktop Client"
!define VERSION "0.01beta1"
!define JARNAME "KontalkDesktopApp.jar"
!define WEBSITE "kontalk.org"
!define ICON "kontalk.ico"
  
;--------------------------------
;General

; The name of the installer
Name "${APPNAME} ${VERSION}"
; The file to write
OutFile "KontalkInstaller.exe"
; The default installation directory
InstallDir $PROGRAMFILES\Kontalk
; The text to prompt the user to enter a directory
;DirText "This will install the Kontalk Desktop Client on your computer. Choose a directory"

BrandingText "${WEBSITE}"

;Java stuff (unused)
 !define JRE_VERSION "1.8"
 !define JRE_URL "http://javadl.sun.com/webapps/download/AutoDL?BundleId=104777"
 ;!include "JREDyna_Inetc.nsh"

;--------------------------------
;Pages

#!insertmacro MUI_PAGE_LICENSE "license.rtf"
  !insertmacro MUI_PAGE_DIRECTORY
  !insertmacro MUI_PAGE_INSTFILES
  !insertmacro MUI_PAGE_FINISH
  
  !insertmacro MUI_UNPAGE_CONFIRM
  !insertmacro MUI_UNPAGE_INSTFILES
  !insertmacro MUI_UNPAGE_FINISH

;--------------------------------
;Languages

  !insertmacro MUI_LANGUAGE "English" ;first language is the default language
  !insertmacro MUI_LANGUAGE "French"
  !insertmacro MUI_LANGUAGE "German"
  !insertmacro MUI_LANGUAGE "Spanish"
  !insertmacro MUI_LANGUAGE "SpanishInternational"
  !insertmacro MUI_LANGUAGE "SimpChinese"
  !insertmacro MUI_LANGUAGE "TradChinese"
  !insertmacro MUI_LANGUAGE "Japanese"
  !insertmacro MUI_LANGUAGE "Korean"
  !insertmacro MUI_LANGUAGE "Italian"
  !insertmacro MUI_LANGUAGE "Dutch"
  !insertmacro MUI_LANGUAGE "Danish"
  !insertmacro MUI_LANGUAGE "Swedish"
  !insertmacro MUI_LANGUAGE "Norwegian"
  !insertmacro MUI_LANGUAGE "NorwegianNynorsk"
  !insertmacro MUI_LANGUAGE "Finnish"
  !insertmacro MUI_LANGUAGE "Greek"
  !insertmacro MUI_LANGUAGE "Russian"
  !insertmacro MUI_LANGUAGE "Portuguese"
  !insertmacro MUI_LANGUAGE "PortugueseBR"
  !insertmacro MUI_LANGUAGE "Polish"
  !insertmacro MUI_LANGUAGE "Ukrainian"
  !insertmacro MUI_LANGUAGE "Czech"
  !insertmacro MUI_LANGUAGE "Slovak"
  !insertmacro MUI_LANGUAGE "Croatian"
  !insertmacro MUI_LANGUAGE "Bulgarian"
  !insertmacro MUI_LANGUAGE "Hungarian"
  !insertmacro MUI_LANGUAGE "Thai"
  !insertmacro MUI_LANGUAGE "Romanian"
  !insertmacro MUI_LANGUAGE "Latvian"
  !insertmacro MUI_LANGUAGE "Macedonian"
  !insertmacro MUI_LANGUAGE "Estonian"
  !insertmacro MUI_LANGUAGE "Turkish"
  !insertmacro MUI_LANGUAGE "Lithuanian"
  !insertmacro MUI_LANGUAGE "Slovenian"
  !insertmacro MUI_LANGUAGE "Serbian"
  !insertmacro MUI_LANGUAGE "SerbianLatin"
  !insertmacro MUI_LANGUAGE "Arabic"
  !insertmacro MUI_LANGUAGE "Farsi"
  !insertmacro MUI_LANGUAGE "Hebrew"
  !insertmacro MUI_LANGUAGE "Indonesian"
  !insertmacro MUI_LANGUAGE "Mongolian"
  !insertmacro MUI_LANGUAGE "Luxembourgish"
  !insertmacro MUI_LANGUAGE "Albanian"
  !insertmacro MUI_LANGUAGE "Breton"
  !insertmacro MUI_LANGUAGE "Belarusian"
  !insertmacro MUI_LANGUAGE "Icelandic"
  !insertmacro MUI_LANGUAGE "Malay"
  !insertmacro MUI_LANGUAGE "Bosnian"
  !insertmacro MUI_LANGUAGE "Kurdish"
  !insertmacro MUI_LANGUAGE "Irish"
  !insertmacro MUI_LANGUAGE "Uzbek"
  !insertmacro MUI_LANGUAGE "Galician"
  !insertmacro MUI_LANGUAGE "Afrikaans"
  !insertmacro MUI_LANGUAGE "Catalan"
  !insertmacro MUI_LANGUAGE "Esperanto"
;  !insertmacro MUI_LANGUAGE "Asturian"
  !insertmacro MUI_LANGUAGE "Basque"
;  !insertmacro MUI_LANGUAGE "Pashto"
;  !insertmacro MUI_LANGUAGE "ScotsGaelic"
;  !insertmacro MUI_LANGUAGE "Georgian"
;  !insertmacro MUI_LANGUAGE "Vietnamese"
  !insertmacro MUI_LANGUAGE "Welsh"
;  !insertmacro MUI_LANGUAGE "Armenian"

; whatever
  !insertmacro MUI_RESERVEFILE_LANGDLL

;--------------------------------
;Installer Sections

; The stuff to install
Section foo sid

; Set output path to the installation directory.
SetOutPath $INSTDIR

; files
File ${JARNAME}
File /r lib 
File ${ICON}

; start menu shortcut
CreateShortCut "$SMPROGRAMS\Kontalk Desktop.lnk" "$INSTDIR\${JARNAME}" "" "$INSTDIR\${ICON}"

; desktop icon
CreateShortCut "$DESKTOP\Kontalk Desktop.lnk" "$INSTDIR\${JARNAME}" "" "$INSTDIR\${ICON}"

; Tell the compiler to write an uninstaller and to look for a "Uninstall" section
WriteUninstaller $INSTDIR\Uninstall.exe

; register uninstaller in system control
!define ARP "Software\Microsoft\Windows\CurrentVersion\Uninstall\KontalkDesktop"
WriteRegStr HKLM "${ARP}" "DisplayName" "${APPNAME}"
WriteRegStr HKLM "${ARP}" "UninstallString" "$\"$INSTDIR\uninstall.exe$\""
WriteRegStr HKLM "${ARP}" "QuietUninstallString" "$\"$INSTDIR\uninstall.exe$\" /S"
WriteRegStr HKLM "${ARP}" "Publisher" "Kontalk Dev Team"
WriteRegStr HKLM "${ARP}" "DisplayVersion" "${VERSION}"
SectionGetSize ${sid} $0
WriteRegDWORD HKLM "${ARP}" "EstimatedSize" "$0"
WriteRegStr HKLM "${ARP}" "HelpLink" "http://${WEBSITE}"
WriteRegStr HKLM "${ARP}" "InstallLocation" "$INSTDIR"
WriteRegDWORD HKLM "${ARP}" "NoModify" 1
WriteRegDWORD HKLM "${ARP}" "NoRepair" 1 

SectionEnd

;--------------------------------
;Uninstaller Section

Section "Uninstall"

; uninstaller itself
Delete $INSTDIR\Uninstall.exe
; uninstaller reg key
DeleteRegKey HKLM "${ARP}"

; start menu shortcut
Delete "$SMPROGRAMS\Kontalk Desktop.lnk"
; desktop icon
Delete "$DESKTOP\Kontalk Desktop.lnk"

; files
Delete "$INSTDIR\${JARNAME}"
Delete "$INSTDIR\lib\*.jar"
RMDir $INSTDIR\lib
Delete $INSTDIR\${ICON}
RMDir $INSTDIR

SectionEnd 
