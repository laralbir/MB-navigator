#!/usr/bin/env bash

# MB-navigator: Script de detección automática e instalación de APK (USB y Wi-Fi)
# Uso: ./scripts/deploy-device.sh [--all] [--connect <ip:puerto>] [--no-launch]

set -euo pipefail

# Colores para la terminal
COLOR_RESET="\033[0m"
COLOR_GREEN="\033[1;32m"
COLOR_YELLOW="\033[1;33m"
COLOR_RED="\033[1;31m"
COLOR_CYAN="\033[1;36m"
COLOR_BOLD="\033[1m"

PACKAGE_NAME="org.mbnavigator.android"
MAIN_ACTIVITY="${PACKAGE_NAME}.MainActivity"

echo -e "${COLOR_CYAN}${COLOR_BOLD}=== MB-navigator: Auto-Deploy Android Device ===${COLOR_RESET}\n"

# 1. Comprobar que adb esté disponible en el PATH
if ! command -v adb &> /dev/null; then
    echo -e "${COLOR_RED}[ERROR] 'adb' no está instalado o no se encuentra en el PATH.${COLOR_RESET}"
    echo "Instala Android Command-line Tools o añade el directorio platform-tools a tu PATH."
    exit 1
fi

adb start-server > /dev/null 2>&1

# Opciones de CLI
DEPLOY_ALL=false
AUTO_LAUNCH=true
TARGET_IP=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --all)
            DEPLOY_ALL=true
            shift
            ;;
        --no-launch)
            AUTO_LAUNCH=false
            shift
            ;;
        --connect)
            TARGET_IP="$2"
            shift 2
            ;;
        *)
            echo "Opción desconocida: $1"
            echo "Uso: $0 [--all] [--no-launch] [--connect <ip:puerto>]"
            exit 1
            ;;
    esac
done

# 2. Conectar por Wi-Fi si se pasa --connect
if [[ -n "$TARGET_IP" ]]; then
    echo -e "${COLOR_YELLOW}[INFO] Conectando a dispositivo en la red: $TARGET_IP ...${COLOR_RESET}"
    adb connect "$TARGET_IP"
fi

# 3. Intentar descubrimiento mDNS para depuración inalámbrica (Android 11+)
if adb mdns check &>/dev/null; then
    echo -e "${COLOR_CYAN}[INFO] Buscando dispositivos con depuración inalámbrica (mDNS)...${COLOR_RESET}"
    MDNS_SERVICES=$(adb mdns services 2>/dev/null || true)
    if [[ -n "$MDNS_SERVICES" ]]; then
        echo -e "${COLOR_GREEN}[mDNS] Dispositivos anunciados en la red:${COLOR_RESET}"
        echo "$MDNS_SERVICES"
    fi
fi

# 4. Listar dispositivos autorizados
echo -e "\n${COLOR_CYAN}[INFO] Escaneando dispositivos conectados (USB y Red)...${COLOR_RESET}"
RAW_DEVICES=$(adb devices -l | tail -n +2 | grep -v "^$" || true)

if [[ -z "$RAW_DEVICES" ]]; then
    echo -e "${COLOR_RED}[AVISO] No se encontró ningún dispositivo conectado.${COLOR_RESET}"
    echo -e "Asegúrate de:"
    echo -e " 1. Habilitar 'Depuración por USB' o 'Depuración inalámbrica' en Opciones de desarrollador."
    echo -e " 2. Conectar el cable USB o emparejar en la misma red Wi-Fi."
    echo -e " 3. Aceptar el mensaje '¿Permitir depuración por USB?' en la pantalla de tu móvil."
    exit 0
fi

# Parsear dispositivos
DEVICE_IDS=()
DEVICE_INFOS=()

while IFS= read -r line; do
    DEVICE_ID=$(echo "$line" | awk '{print $1}')
    STATUS=$(echo "$line" | awk '{print $2}')
    EXTRA_INFO=$(echo "$line" | cut -d' ' -f3-)

    if [[ "$STATUS" == "unauthorized" ]]; then
        echo -e "${COLOR_YELLOW}[!] Dispositivo $DEVICE_ID sin autorizar. Acepta el aviso en la pantalla del dispositivo.${COLOR_RESET}"
        continue
    elif [[ "$STATUS" == "offline" ]]; then
        echo -e "${COLOR_RED}[!] Dispositivo $DEVICE_ID está fuera de línea (offline).${COLOR_RESET}"
        continue
    elif [[ "$STATUS" == "device" ]]; then
        DEVICE_IDS+=("$DEVICE_ID")
        DEVICE_INFOS+=("$DEVICE_ID ($EXTRA_INFO)")
    fi
done <<< "$RAW_DEVICES"

NUM_DEVICES=${#DEVICE_IDS[@]}

if [[ $NUM_DEVICES -eq 0 ]]; then
    echo -e "${COLOR_RED}[ERROR] No hay dispositivos autorizados en estado 'device'.${COLOR_RESET}"
    exit 1
fi

SELECTED_DEVICES=()

if [[ "$DEPLOY_ALL" == true ]]; then
    SELECTED_DEVICES=("${DEVICE_IDS[@]}")
    echo -e "${COLOR_GREEN}[INFO] Modo --all activo. Desplegando en $NUM_DEVICES dispositivo(s).${COLOR_RESET}"
elif [[ $NUM_DEVICES -eq 1 ]]; then
    SELECTED_DEVICES=("${DEVICE_IDS[0]}")
    echo -e "${COLOR_GREEN}[INFO] Dispositivo único detectado: ${DEVICE_INFOS[0]}${COLOR_RESET}"
else
    echo -e "\n${COLOR_BOLD}Dispositivos disponibles:${COLOR_RESET}"
    for i in "${!DEVICE_INFOS[@]}"; do
        echo -e " [$((i + 1))] ${DEVICE_INFOS[$i]}"
    done
    echo -e " [A] Desplegar en TODOS los dispositivos"
    echo -ne "\n${COLOR_YELLOW}Selecciona una opción [1-$NUM_DEVICES / A]: ${COLOR_RESET}"
    read -r CHOICE

    if [[ "$CHOICE" == "A" || "$CHOICE" == "a" ]]; then
        SELECTED_DEVICES=("${DEVICE_IDS[@]}")
    elif [[ "$CHOICE" =~ ^[0-9]+$ ]] && [ "$CHOICE" -ge 1 ] && [ "$CHOICE" -le "$NUM_DEVICES" ]; then
        INDEX=$((CHOICE - 1))
        SELECTED_DEVICES=("${DEVICE_IDS[$INDEX]}")
    else
        echo -e "${COLOR_RED}[ERROR] Opción no válida. Cancelando.${COLOR_RESET}"
        exit 1
    fi
fi

# 5. Localizar o compilar la APK
# Si existe ./gradlew, se puede compilar
APK_PATH=""
CANDIDATE_PATHS=(
    "apps/android-navigator/app/build/outputs/apk/debug/app-debug.apk"
    "apps/android-navigator/build/outputs/apk/debug/android-navigator-debug.apk"
)

for path in "${CANDIDATE_PATHS[@]}"; do
    if [[ -f "$path" ]]; then
        APK_PATH="$path"
        break
    fi
done

if [[ -z "$APK_PATH" ]]; then
    if [[ -f "./gradlew" ]]; then
        echo -e "\n${COLOR_CYAN}[INFO] Compilando APK con Gradle...${COLOR_RESET}"
        ./gradlew assembleDebug
        for path in "${CANDIDATE_PATHS[@]}"; do
            if [[ -f "$path" ]]; then
                APK_PATH="$path"
                break
            fi
        done
    fi
fi

if [[ -z "$APK_PATH" || ! -f "$APK_PATH" ]]; then
    echo -e "${COLOR_YELLOW}[AVISO] No se encontró una APK precompilada en las rutas habituales.${COLOR_RESET}"
    echo "Ruta buscada: apps/android-navigator/build/outputs/apk/debug/"
    echo "Cuando el módulo Android esté configurado y compile, este script instalará el artefacto generado."
    exit 0
fi

# 6. Instalar y ejecutar en los dispositivos seleccionados
for TARGET_DEV in "${SELECTED_DEVICES[@]}"; do
    echo -e "\n${COLOR_CYAN}--------------------------------------------------${COLOR_RESET}"
    echo -e "${COLOR_GREEN}Instalando en dispositivo: $TARGET_DEV ...${COLOR_RESET}"
    
    if adb -s "$TARGET_DEV" install -r -d -g "$APK_PATH"; then
        echo -e "${COLOR_GREEN}[OK] Instalación completada con éxito en $TARGET_DEV.${COLOR_RESET}"
        
        if [[ "$AUTO_LAUNCH" == true ]]; then
            echo -e "${COLOR_CYAN}Iniciando aplicación ($MAIN_ACTIVITY)...${COLOR_RESET}"
            adb -s "$TARGET_DEV" shell am start -n "${PACKAGE_NAME}/${MAIN_ACTIVITY}" || true
        fi
    else
        echo -e "${COLOR_RED}[FALLO] Error al instalar en $TARGET_DEV.${COLOR_RESET}"
    fi
done

echo -e "\n${COLOR_GREEN}${COLOR_BOLD}=== Proceso finalizado ===${COLOR_RESET}"
