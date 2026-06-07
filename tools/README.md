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
