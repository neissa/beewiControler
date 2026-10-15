# BeewiControler

Application Android (Kotlin, Jetpack Compose, Material 3) qui pilote une voiture télécommandée BeeWi « Mini Cooper » via Bluetooth classique (SPP / RFCOMM).

> Le protocole est celui du Bluetooth classique (SPP). Ce n'est pas du BLE : aucun service GATT n'est utilisé.

## Fonctionnalités

- **Choix de la voiture** : liste des appareils Bluetooth appairés et détectés par une recherche.
- **Repérage des voitures probables** : les appareils qui semblent être des jouets ou des voitures sont affichés en premier, avec le libellé « Likely car ». Le critère repose sur le nom (BeeWi, BBZ, Cooper, Mini, car, toy, rc, voiture), la classe Bluetooth « Toy » ou le préfixe MAC `00:13:EF`.
- **Mémorisation** : la voiture choisie est conservée entre deux lancements.
- **Contrôle à deux joysticks** :
  - joystick gauche (accélérateur) : haut = avancer, bas = reculer ;
  - joystick droit (direction) : gauche = tourner à gauche, droite = tourner à droite.
  - Plusieurs commandes peuvent être actives en même temps (par exemple avancer et tourner à droite).
- **Arrêt de sécurité** : la voiture s'arrête quand on relâche un joystick, et aussi quand l'application passe en arrière-plan.
- **Mise en page adaptative** : portrait (statut au-dessus des joysticks) et paysage (joystick gauche | statut | joystick droit).
- **Connexion** : bouton Connect / Disconnect. Le bouton « Change car » ramène à la liste quand on n'est pas connecté.

## Utilisation

1. Allumer la voiture.
2. Appairer la voiture dans les réglages Bluetooth d'Android (une seule fois).
3. Lancer l'application et accorder les autorisations Bluetooth demandées (sous Android 11 et moins, l'autorisation de localisation est aussi demandée, car la recherche Bluetooth en a besoin).
4. Choisir la voiture dans la liste, ou lancer une recherche avec **Scan** si elle n'apparaît pas.
5. Toucher **Continue**, puis **Connect**.
6. Déplacer les joysticks pour conduire. Relâcher un joystick le ramène au centre et arrête le mouvement correspondant.

> Pour un premier essai, soulevez la voiture pour que les roues ne touchent pas le sol.

## Détails techniques

- Connexion RFCOMM au service SPP (UUID `00001101-0000-1000-8000-00805F9B34FB`), puis envoi de l'octet d'initialisation `0xFF` et d'une pause de 200 ms.
- Octets de commande, appui / relâchement :

| Commande | Appui | Relâchement |
|----------|-------|-------------|
| Avancer  | 1     | 0           |
| Reculer  | 3     | 2           |
| Gauche   | 5     | 4           |
| Droite   | 7     | 6           |

- Tant qu'une commande est active, son octet d'appui est renvoyé environ toutes les 10 ms.
- Toutes les écritures passent par un même mutex, pour qu'un octet d'arrêt ne soit jamais dépassé par un octet d'appui.
- Source du protocole : [BeeWi Mini Ctrl](https://github.com/jimmckeeth/BeeMiniCtrl) (Delphi).

## Structure du code

| Fichier | Rôle |
|---------|------|
| `bluetooth/BeewiConnection.kt` | Socket RFCOMM, initialisation et écriture des octets |
| `bluetooth/CarDeviceScanner.kt` | Appareils appairés et recherche Bluetooth |
| `bluetooth/CarDevice.kt` | Modèle d'appareil et heuristique « voiture probable » |
| `bluetooth/CarPreferences.kt` | Mémorisation de la voiture choisie |
| `bluetooth/CarCommand.kt`, `CarConfig.kt` | Octets de commande et constantes |
| `bluetooth/BluetoothPermissions.kt` | Autorisations à demander |
| `viewmodel/CarViewModel.kt` | État, boucle de commande, arrêt de sécurité |
| `ui/CarSelectionScreen.kt` | Écran de choix de la voiture |
| `ui/CarControlScreen.kt` | Joysticks et statut de connexion |

## Compilation

```bash
./gradlew :app:assembleDebug
```

Le APK est généré dans `app/build/outputs/apk/debug/`.

Prérequis : JDK 17 ou plus récent (le projet a été testé avec un JDK 25), Android Studio récent, `compileSdk` 36, `minSdk` 26.
