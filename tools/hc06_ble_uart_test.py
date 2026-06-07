import argparse
import asyncio
import pathlib
import sys
import time


sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent / ".pydeps"))

from bleak import BleakClient, BleakScanner  # noqa: E402


UART_SERVICE_UUID = "0000ffe0-0000-1000-8000-00805f9b34fb"
UART_CHARACTERISTIC_UUID = "0000ffe1-0000-1000-8000-00805f9b34fb"


async def scan_devices(timeout):
    print(f"Escaneando BLE por {timeout:.1f}s...")
    devices = await BleakScanner.discover(timeout=timeout, return_adv=True)
    for address, (device, advertisement) in devices.items():
        service_uuids = ", ".join(advertisement.service_uuids or [])
        print(f"{device.name or 'Sem nome'} | {address} | {service_uuids}")
    return devices


async def resolve_address(address, name, timeout):
    if address:
        return address

    devices = await scan_devices(timeout)
    lowered_name = name.lower()
    for found_address, (device, advertisement) in devices.items():
        device_name = device.name or advertisement.local_name or ""
        if lowered_name in device_name.lower():
            print(f"Encontrado por nome: {device_name} em {found_address}")
            return found_address

    raise RuntimeError(f"Nenhum dispositivo com nome contendo {name!r} foi encontrado.")


async def run_test(args):
    if args.scan:
        await scan_devices(args.timeout)
        return

    address = await resolve_address(args.address, args.name, args.timeout)
    command = args.command if args.command.endswith("\n") else args.command + "\n"

    print(f"Conectando BLE em {address}...")
    async with BleakClient(address, timeout=args.timeout) as client:
        print(f"Conectado: {client.is_connected}")
        services = client.services
        for service in services:
            print(f"Servico: {service.uuid}")
            for characteristic in service.characteristics:
                print(f"  Caracteristica: {characteristic.uuid} props={characteristic.properties}")

        print(f"Enviando em FFE1: {command.strip()}")
        await client.write_gatt_char(
            UART_CHARACTERISTIC_UUID,
            command.encode("ascii"),
            response=False,
        )
        time.sleep(args.hold)
        print("Enviando parada: F0T0D0E0")
        await client.write_gatt_char(
            UART_CHARACTERISTIC_UUID,
            b"F0T0D0E0\n",
            response=False,
        )

    print("Teste BLE concluido.")


def main():
    parser = argparse.ArgumentParser(description="Testa BLE UART FFE0/FFE1 no modulo do carrinho.")
    parser.add_argument("--address", help="Endereco BLE/MAC do modulo.")
    parser.add_argument("--name", default="HC-06", help="Nome para buscar se --address nao for informado.")
    parser.add_argument("--command", default="F120T0D0E0", help="Comando a enviar.")
    parser.add_argument("--hold", type=float, default=0.5, help="Tempo antes de enviar parada.")
    parser.add_argument("--timeout", type=float, default=10.0, help="Timeout de scan/conexao.")
    parser.add_argument("--scan", action="store_true", help="Apenas lista dispositivos BLE.")
    args = parser.parse_args()

    try:
        asyncio.run(run_test(args))
    except Exception as error:
        print(f"Falha BLE: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
