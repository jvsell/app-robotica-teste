import argparse
import socket
import sys
import time


SPP_UUID_CHANNELS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]


def connect(address, channels):
    last_error = None
    for channel in channels:
        print(f"Tentando RFCOMM canal {channel}...")
        bt_socket = socket.socket(
            socket.AF_BLUETOOTH,
            socket.SOCK_STREAM,
            socket.BTPROTO_RFCOMM,
        )
        bt_socket.settimeout(8)
        try:
            bt_socket.connect((address, channel))
            print(f"Conectado em {address}, canal {channel}.")
            return bt_socket
        except OSError as error:
            last_error = error
            print(f"Canal {channel} falhou: {error}")
            bt_socket.close()
            time.sleep(0.5)

    raise OSError(f"Nenhum canal conectou. Ultimo erro: {last_error}")


def send_command(bt_socket, command):
    if not command.endswith("\n"):
        command += "\n"
    print(f"Enviando: {command.strip()}")
    bt_socket.sendall(command.encode("ascii"))


def main():
    parser = argparse.ArgumentParser(
        description="Testa conexao Bluetooth classica RFCOMM com HC-06."
    )
    parser.add_argument("address", help="MAC do HC-06, exemplo: 98:D3:31:AA:BB:CC")
    parser.add_argument(
        "--command",
        default="F120T0D0E0",
        help="Comando para enviar ao Arduino. Padrao: F120T0D0E0",
    )
    parser.add_argument(
        "--channels",
        default=",".join(str(channel) for channel in SPP_UUID_CHANNELS),
        help="Canais RFCOMM para tentar, separados por virgula. Padrao: 1..10",
    )
    parser.add_argument(
        "--hold",
        type=float,
        default=0.5,
        help="Tempo em segundos antes de enviar parada. Padrao: 0.5",
    )
    args = parser.parse_args()

    channels = [int(value.strip()) for value in args.channels.split(",") if value.strip()]

    try:
        with connect(args.address, channels) as bt_socket:
            send_command(bt_socket, args.command)
            time.sleep(args.hold)
            send_command(bt_socket, "F0T0D0E0")
            print("Teste concluido.")
    except OSError as error:
        print(f"Falha: {error}", file=sys.stderr)
        return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
