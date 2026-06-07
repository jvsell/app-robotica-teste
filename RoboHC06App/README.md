# Robo HC-06

Aplicativo Android simples para controlar o sketch `v2.ino` por Bluetooth classico SPP usando um modulo HC-06.

## Como usar

1. Pareie o HC-06 nas configuracoes do Android. A senha comum e `1234` ou `0000`.
2. Abra este projeto no Android Studio.
3. Gere e instale o app no celular.
4. Selecione o HC-06 pareado e toque em `Conectar`.
5. Use o joystick. O app envia comandos como `F120T0D30E0\n`.

## Gerar APK pelo GitHub Actions

1. Crie um repositorio no GitHub e envie esta pasta do sketch, incluindo `RoboHC06App` e `.github`.
2. No GitHub, abra `Actions`.
3. Rode o workflow `Build Robo HC-06 APK` manualmente em `Run workflow`, ou faca um push.
4. Ao final, baixe o artefato `robo-hc06-debug-apk`.
5. Instale o `app-debug.apk` no celular autorizando fontes desconhecidas.

## Protocolo enviado

- `F` controla frente.
- `T` controla tras.
- `D` controla direita.
- `E` controla esquerda.
- Cada valor vai de `0` a `255`.
- A mensagem termina com quebra de linha para o `readStringUntil('\n')` do Arduino.

## Observacoes

O app lista dispositivos ja pareados. Isso evita depender de varredura Bluetooth, que costuma ser a parte mais instavel em Android recente.

Se a conexao falhar, feche outros apps Bluetooth serial que possam estar conectados ao HC-06. O modulo aceita apenas uma conexao ativa por vez.

Alguns modulos vendidos como HC-06 aparecem no Windows/Android como BLE UART (`FFE0`/`FFE1`/`FFE2`) em vez de Bluetooth classico SPP. O app tenta BLE UART primeiro, prefere `FFE2` para escrita quando existir, e usa RFCOMM como fallback depois de timeout.
