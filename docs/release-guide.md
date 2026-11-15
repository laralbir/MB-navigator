# Guía de Firma y Release para Google Play Store

Esta guía detalla el proceso de preparación, firma criptográfica y publicación de la aplicación nativa Android de **MB-navigator**.

---

## 1. Estrategia de Versionado (SemVer)

Se utiliza Semantic Versioning (`MAJOR.MINOR.PATCH`):
- `versionCode`: Entero incremental único para Google Play (ej. `10001`).
- `versionName`: Versión visible para los usuarios (ej. `1.0.1`).

En Gradle se puede calcular dinámicamente o mantener en un archivo `version.properties`.

---

## 2. Firma Criptográfica de la App (AAB - Android App Bundle)

Google Play exige el formato `.aab` y recomienda Google Play App Signing:
1. **Keystore de subida (Upload Key)**: Se usa exclusivamente para firmar el archivo `.aab` que se sube a Play Console.
2. **Keystore de firma de app**: Almacenada de forma segura por Google en la infraestructura de Play Console.

### Configuración en Gradle (`build.gradle.kts`)
El archivo de Gradle no debe incluir contraseñas ni rutas absolutas en texto plano. Se emplean variables de entorno:

```kotlin
signingConfigs {
    create("release") {
        storeFile = file(System.getenv("KEYSTORE_PATH") ?: "upload-keystore.jks")
        storePassword = System.getenv("KEYSTORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS")
        keyPassword = System.getenv("KEY_PASSWORD")
    }
}
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
        signingConfig = signingConfigs.getByName("release")
    }
}
```

---

## 3. Automatización de Despliegue (CI/CD)

- **GitHub Secrets requeridos**:
  - `PLAY_STORE_JSON_KEY`: Service account de Google Cloud con permisos en Play Console.
  - `UPLOAD_KEYSTORE_BASE64`: Archivo JKS codificado en Base64.
  - `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

- **Herramientas recomendadas para la publicación**:
  - `Gradle Play Publisher (GPP)`: Plugin oficial de Gradle para subir AABs, notas de versión y capturas directamente a pistas internas (*internal*), cerradas (*alpha/beta*) o producción.
  - Alternativa: Fastlane con la acción `upload_to_play_store`.
