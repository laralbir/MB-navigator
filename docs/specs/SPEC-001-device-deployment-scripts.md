# SPEC-001: Script de Detección e Instalación de APK en Dispositivos (USB y Wi-Fi)

- **Estado**: Aprobado
- **App de destino**: Android Navigator (`apps/android-navigator`) / Herramientas de desarrollo
- **Fecha**: 2026-11-15
- **Autores**: MB-navigator Core Team

---

## 1. Resumen Ejecutivo
Para agilizar el ciclo de desarrollo, pruebas de campo y QA, se requiere un conjunto de scripts automatizados capaces de detectar dispositivos Android conectados físicamente por USB o presentes en la red local (depuración inalámbrica mediante Wi-Fi / mDNS), compilar la aplicación e instalar la APK/AAB correspondiente sin intervención manual compleja.

---

## 2. Requisitos Funcionales

- **RF-1 (Detección USB)**: El script debe consultar `adb devices` y listar todos los terminales conectados por cable con su modelo, fabricante y estado (`device`, `unauthorized`, `offline`).
- **RF-2 (Detección Inalámbrica / Wi-Fi)**:
  - Soporte para escaneo de servicios mDNS (`adb mdns services`) introducido en Android 11+ para emparejamiento y conexión automática.
  - Capacidad de conectar manualmente a una IP y puerto (`adb connect <ip>:<puerto>`) o reconectar a dispositivos previamente autorizados en la subred.
- **RF-3 (Selección de Dispositivo)**:
  - Si se detecta un único dispositivo, el despliegue es inmediato.
  - Si se detectan múltiples dispositivos, se presentará un menú interactivo numerado para seleccionar el destino o una opción para desplegar en todos en paralelo (`--all`).
- **RF-4 (Compilación e Instalación)**:
  - Compilación automática con Gradle (`./gradlew :apps:android-navigator:assembleDebug`).
  - Instalación con reemplazo de datos previos (`adb install -r -d -g`).
  - Lanzamiento automático de la actividad principal (`MainActivity`) tras la instalación exitosa.

---

## 3. Requisitos No Funcionales

- **RNF-1 (Portabilidad)**: El script debe ejecutarse de forma transparente en entornos POSIX (macOS con Zsh/Bash y Linux).
- **RNF-2 (Feedback Visual)**: Códigos de salida coloreados (Verde: éxito, Amarillo: advertencia, Rojo: fallo de autorización o adb no encontrado).
- **RNF-3 (Idempotencia)**: Debe reiniciar el servidor ADB (`adb start-server`) si el daemon local no responde.

---

## 4. Diseño del Flujo de Ejecución

```text
       +-------------------------------+
       | Ejecutar scripts/deploy.sh    |
       +-------------------------------+
                       |
             [¿ADB instalado?]
               /              \
            (No)              (Sí)
             /                  \
   [Error fatal y salir]    [Comprobar daemon ADB]
                                |
               +----------------+----------------+
               |                                 |
         [Escaneo USB]                   [Escaneo mDNS / Wi-Fi]
               |                                 |
               +----------------+----------------+
                                |
                    [¿Cuántos dispositivos?]
                     /         |          \
                 (0)          (1)        (> 1)
                 /             |            \
    [Esperar conexión]   [Auto-selección] [Menú interactivo o --all]
                                \           /
                             +-----------------+
                             | Compilar Gradle |
                             +-----------------+
                                     |
                             +-----------------+
                             | adb install -r  |
                             +-----------------+
                                     |
                             +-----------------+
                             | Iniciar App     |
                             +-----------------+
```

---

## 5. Casos de Prueba (TDD / Criterios de Aceptación)

- [ ] `test_no_devices_detected_displays_helpful_troubleshooting_message`
- [ ] `test_single_usb_device_installs_and_launches_without_prompt`
- [ ] `test_multiple_devices_shows_selection_menu`
- [ ] `test_flag_all_installs_concurrently_on_all_connected_targets`
- [ ] `test_unauthorized_device_warns_user_to_accept_rsa_prompt`
