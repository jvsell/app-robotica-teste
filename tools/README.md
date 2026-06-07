# Ferramentas de teste

## Testar HC-06 pelo Bluetooth do Windows

Use `hc06_rfcomm_test.py` para testar se o PC consegue abrir uma conexao RFCOMM com o HC-06 e enviar o mesmo protocolo do `v2.ino`.

```powershell
python .\tools\hc06_rfcomm_test.py "98:D3:31:AA:BB:CC"
```

Para mandar um comando especifico:

```powershell
python .\tools\hc06_rfcomm_test.py "98:D3:31:AA:BB:CC" --command "F150T0D0E0"
```

O script tenta canais RFCOMM `1` a `10`. Se conectar, o monitor serial do Arduino deve mostrar a mensagem recebida.

Antes de rodar, feche apps Bluetooth serial no celular, porque o HC-06 aceita apenas uma conexao ativa por vez.

## Testar BLE UART FFE0/FFE1 pelo Windows

Se o modulo aparecer como BLE, use:

```powershell
python .\tools\hc06_ble_uart_test.py --scan
python .\tools\hc06_ble_uart_test.py --address "8E:E9:ED:0A:EA:0C"
```

No teste feito neste PC, o `HC-06` apareceu como BLE em `8E:E9:ED:0A:EA:0C`, com servico `FFE0` e caracteristicas `FFE1`/`FFE2`. O script usa `FFE2` por padrao para escrita.
