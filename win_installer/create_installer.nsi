; NSIS Kontalk install script

;NSIS Modern User Interface
;Multilingual Example Script

; simple Java installation, modified version of
; http://nsis.sourceforge.net/New_installer_with_JRE_check_%28includes_fixes_from_%27Simple_installer_with_JRE_check%27_and_missing_jre.ini%29

;  !include "Library.nsh"
!include "MUI.nsh"

;--------------------------------
;Defines  
  
!define APPNAME "Kontalk Desktop Client"
!define VERSION "3.1"
!define JARNAME "KontalkDesktopApp.jar"
!define WEBSITE "kontalk.org"
!define ICON "kontalk.ico"
!define INP_DIR "..\dist" 

;Java
!define JRE_VERSION "1.8"
!define JRE_FILE "jre-8u40-windows-i586-iftw.exe"
Var InstallJRE
  
;--------------------------------
;General

; The name of the installer
Name "${APPNAME} ${VERSION}"
; The file to write
OutFile "KontalkInstaller-${VERSION}.exe"
; The default installation directory
InstallDir $PROGRAMFILES\Kontalk

BrandingText "${WEBSITE}"

;--------------------------------
;Pages

; Java
  Page custom CheckInstalledJRE
  !insertmacro MUI_PAGE_INSTFILES
  !define MUI_PAGE_CUSTOMFUNCTION_PRE myPreInstfiles
  !define MUI_PAGE_CUSTOMFUNCTION_LEAVE RestoreSections

; Kontalk
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

; Java installation
Section -installjre jre
  Push $0
  Push $1

;  MessageBox MB_OK "Inside JRE Section"
  Strcmp $InstallJRE "yes" InstallJRE End

InstallJRE:
  File /oname=$TEMP\jre_setup.exe "${JRE_FILE}"
  Exec '"$TEMP\jre_setup.exe"'

  DetailPrint "Setup finished"
  Delete "$TEMP\jre_setup.exe"
  Goto End

End:
  Pop $1	; Restore $1
  Pop $0	; Restore $0
SectionEnd

; The stuff to install
Section foo SID

; Set output path to the installation directory.
SetOutPath $INSTDIR

; files
File ${INP_DIR}\${JARNAME}
File /r ${INP_DIR}\lib 
File ${ICON}

; shortcuts for all user
SetShellVarContext all
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
SectionGetSize ${SID} $0
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

SetShellVarContext all
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

;--------------------------------
;Installer Functions

Function .onInit
  !insertmacro SelectSection ${jre}
  !insertmacro UnselectSection ${SID}
FunctionEnd

Function myPreInstfiles
  Call RestoreSections
  SetAutoClose true
FunctionEnd

Function RestoreSections
  !insertmacro UnselectSection ${jre}
  !insertmacro SelectSection ${SID}
FunctionEnd

Function CheckInstalledJRE
  Push "${JRE_VERSION}"
  Call DetectJRE
  Exch $0	; Get return value from stack
  StrCmp $0 "0" NoFound
  StrCmp $0 "-1" NoFound
  Goto JREAlreadyInstalled
NoFound:
  Exch $0	; $0 now has the installoptions page return value
  ; Do something with return value here
  Pop $0	; Restore $0
  StrCpy $InstallJRE "yes"
  Return
JREAlreadyInstalled:
  StrCpy $InstallJRE "no"
  Pop $0		; Restore $0
  Return
FunctionEnd

; Returns: 0 - JRE not found. -1 - JRE found but too old. Otherwise - Path to JAVA EXE
; DetectJRE. Version requested is on the stack.
; Returns (on stack)	"0" on failure (java too old or not installed), otherwise path to java interpreter
; Stack value will be overwritten!
Function DetectJRE
  Exch $0	; Get version requested
		; Now the previous value of $0 is on the stack, and the asked for version of JDK is in $0
  Push $1	; $1 = Java version string (ie 1.5.0)
  Push $2	; $2 = Javahome
  Push $3	; $3 and $4 are used for checking the major/minor version of java
  Push $4
  ReadRegStr $1 HKLM "SOFTWARE\JavaSoft\Java Runtime Environment" "CurrentVersion"
  StrCmp $1 "" DetectTry2
  ReadRegStr $2 HKLM "SOFTWARE\JavaSoft\Java Runtime Environment\$1" "JavaHome"
  StrCmp $2 "" DetectTry2
  Goto GetJRE
DetectTry2:
  ReadRegStr $1 HKLM "SOFTWARE\JavaSoft\Java Development Kit" "CurrentVersion"
  StrCmp $1 "" NoFound
  ReadRegStr $2 HKLM "SOFTWARE\JavaSoft\Java Development Kit\$1" "JavaHome"
  StrCmp $2 "" NoFound
GetJRE:
; $0 = version requested. $1 = version found. $2 = javaHome
  IfFileExists "$2\bin\java.exe" 0 NoFound
  StrCpy $3 $0 1			; Get major version. Example: $1 = 1.5.0, now $3 = 1
  StrCpy $4 $1 1			; $3 = major version requested, $4 = major version found
  IntCmp $4 $3 0 FoundOld FoundNew
  StrCpy $3 $0 1 2
  StrCpy $4 $1 1 2			; Same as above. $3 is minor version requested, $4 is minor version installed
  IntCmp $4 $3 FoundNew FoundOld FoundNew
NoFound:
  Push "0"
  Goto DetectJREEnd
FoundOld:
;  Push ${TEMP2}
  Push "-1"
  Goto DetectJREEnd
FoundNew:
  Push "$2\bin\java.exe"
;  Push "OK"
;  Return
   Goto DetectJREEnd
DetectJREEnd:
	; Top of stack is return value, then r4,r3,r2,r1
	Exch	; => r4,rv,r3,r2,r1,r0
	Pop $4	; => rv,r3,r2,r1r,r0
	Exch	; => r3,rv,r2,r1,r0
	Pop $3	; => rv,r2,r1,r0
	Exch 	; => r2,rv,r1,r0
	Pop $2	; => rv,r1,r0
	Exch	; => r1,rv,r0
	Pop $1	; => rv,r0
	Exch	; => r0,rv
	Pop $0	; => rv
FunctionEnd
