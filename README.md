# Liquid Lock

Écran verrouillé Android style **Liquid Glass** (iOS 26), pensé pour Honor / MagicOS.
Il s'affiche par-dessus l'écran verrouillé d'origine et cache l'horloge Honor.

## Installer (sans PC)

1. Crée un dépôt GitHub (ex. `liquid-lock`) et envoie **tout le contenu de ce dossier** (y compris `.github` et `keystore`).
2. Onglet **Actions** : la compilation démarre toute seule (≈ 4 min).
3. Onglet **Releases** : télécharge `LiquidLock.apk` sur ton téléphone et installe-le.

Les mises à jour s'installent par-dessus (clé de signature fixe dans `keystore/`).

## Premier lancement

1. Active **Activer Liquid Lock** et accepte les autorisations (notifications, « afficher par-dessus », batterie).
2. Fais le réglage Honor indiqué en bas de l'app (Lancement d'applis + cadenas dans les apps récentes).
3. Éteins l'écran puis rallume : Liquid Lock est là. Glisse vers le haut → code / empreinte Honor.

## Options

Thèmes prêts (Cristal, Givre, Néon, Minuit, Éditorial, Montre) · fond dégradé ou photo · assombrir / flou du fond ·
4 styles d'horloge (empilée, classique, compacte, aiguilles) · 5 polices · épaisseur · taille · position · alignement ·
24 h · secondes · verre sur les chiffres, en carte ou les deux · 8 teintes · flou, givre, reflets, arrondi, ombre ·
date (3 formats) · batterie · raccourcis lampe et caméra (appui long) · reflets qui bougent avec le gyroscope ·
brillance au réveil · disparition automatique après visage / empreinte.

## Limites

Android n'autorise aucune app à supprimer le vrai écran verrouillé : la sécurité (code, empreinte, visage) reste celle d'Honor.
Les notifications ne s'affichent pas sur Liquid Lock (elles sont sur l'écran Honor, juste derrière).
