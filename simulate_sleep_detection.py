#!/usr/bin/env python3
"""
Simulador de notificación de sueño desde Amazfit Zepp OS.
Este script simula lo que el Side Service del reloj enviaría a la app Android.

Uso:
    # Si corres el script EN EL TELÉFONO (Termux, Pydroid, etc.):
    python3 simulate_sleep_detection.py
    
    # Si corres el script desde tu PC (Windows/Mac/Linux):
    python3 simulate_sleep_detection.py --ip 192.168.1.45
    
    # Descubrir la IP del teléfono automáticamente (si están en la misma red):
    python3 simulate_sleep_detection.py --auto-discover
"""

import requests
import json
import time
import argparse
import socket
from datetime import datetime, timedelta

def discover_phone_ip(port=50002):
    """Intenta descubrir la IP del teléfono en la red local escaneando puertos abiertos."""
    import subprocess
    import re
    
    # Obtener el rango de red local
    try:
        result = subprocess.run(['ipconfig'], capture_output=True, text=True)
        output = result.stdout
        
        # Buscar IPs que empiecen con 192.168.x.x o 10.x.x.x
        ip_pattern = r'(192\.168\.\d+\.\d+|10\.\d+\.\d+\.\d+)'
        local_ips = re.findall(ip_pattern, output)
        
        if not local_ips:
            print("❌ No se pudo determinar la red local")
            return None
            
        # Tomar la primera IP local y extraer el prefijo de red
        local_ip = local_ips[0]
        network_prefix = '.'.join(local_ip.split('.')[:3])
        
        print(f"🔍 Escaneando red {network_prefix}.x en busca del teléfono...")
        
        # Escanear IPs en la red
        for i in range(1, 255):
            test_ip = f"{network_prefix}.{i}"
            try:
                sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                sock.settimeout(0.1)
                result = sock.connect_ex((test_ip, port))
                sock.close()
                
                if result == 0:
                    print(f"✅ Teléfono encontrado en: {test_ip}")
                    return test_ip
            except:
                pass
                
        print("❌ No se encontró el teléfono en la red local")
        return None
        
    except Exception as e:
        print(f"❌ Error al descubrir IP: {e}")
        return None

def main():
    parser = argparse.ArgumentParser(
        description='Simulador de detección de sueño Amazfit',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog='''
Ejemplos:
  # Teléfono y PC en la misma red (PC → Teléfono):
  python3 simulate_sleep_detection.py --ip 192.168.1.45
  
  # Script corre EN EL TELÉFONO:
  python3 simulate_sleep_detection.py
  
  # Auto-descubrir IP del teléfono:
  python3 simulate_sleep_detection.py --auto-discover
  
  # Simular que te dormiste hace 10 minutos:
  python3 simulate_sleep_detection.py --ip 192.168.1.45 --minutes-ago 10
        '''
    )
    
    parser.add_argument(
        '--ip', 
        type=str, 
        default='localhost',
        help='IP del teléfono donde corre la app Android (default: localhost)'
    )
    parser.add_argument(
        '--port', 
        type=int, 
        default=50002,
        help='Puerto donde escucha la app Android (default: 50002)'
    )
    parser.add_argument(
        '--auto-discover', 
        action='store_true',
        help='Intenta descubrir automáticamente la IP del teléfono'
    )
    parser.add_argument(
        '--minutes-ago', 
        type=int, 
        default=5,
        help='Minutos atrás para simular el inicio de sueño (default: 5)'
    )
    
    args = parser.parse_args()
    
    # Auto-descubrir si se solicitó
    if args.auto_discover:
        discovered_ip = discover_phone_ip(args.port)
        if discovered_ip:
            args.ip = discovered_ip
        else:
            print("⚠️  Usando localhost como fallback")
            print("   Si la app está en el teléfono, usa --ip <IP_DEL_TELÉFONO>")
    
    # Construir URL
    url = f"http://{args.ip}:{args.port}/sleep"
    
    # Calcular el tiempo de inicio de sueño
    now = datetime.now()
    sleep_time = now - timedelta(minutes=args.minutes_ago)
    
    # Convertir a minutos desde medianoche (formato de Zepp OS)
    sleep_onset_minutes = sleep_time.hour * 60 + sleep_time.minute
    
    # Timestamp en milisegundos
    timestamp_ms = int(time.time() * 1000)
    
    payload = {
        "sleepOnsetMinutes": sleep_onset_minutes,
        "timestamp": timestamp_ms
    }
    
    print("=" * 60)
    print("SIMULADOR DE DETECCIÓN DE SUEÑO - AMAZFIT")
    print("=" * 60)
    print(f"\n📱 Target: {url}")
    print(f"🕐 Hora actual: {now.strftime('%H:%M:%S')}")
    print(f"😴 Simulando sueño: {sleep_time.strftime('%H:%M:%S')} ({args.minutes_ago} min atrás)")
    print(f"📝 sleepOnsetMinutes: {sleep_onset_minutes}")
    print(f"📦 Payload: {json.dumps(payload, indent=2)}")
    
    # Verificar si es localhost
    if args.ip == 'localhost' or args.ip == '127.0.0.1':
        print("\n⚠️  ATENCIÓN: Estás usando localhost")
        print("   Esto funciona SOLO si el script corre EN EL TELÉFONO")
        print("   Si corres desde PC, usa: --ip <IP_DEL_TELÉFONO>")
        print()
    
    try:
        print("🚀 Enviando notificación...")
        response = requests.post(
            url,
            json=payload,
            headers={"Content-Type": "application/json"},
            timeout=10
        )
        
        print(f"\n✅ Respuesta: HTTP {response.status_code}")
        if response.text:
            print(f"   Body: {response.text}")
        
        if response.status_code == 200:
            print("\n🎉 ÉXITO! La app Android debería haber:")
            print("   1. Pausado la reproducción")
            print(f"   2. Retrocedido ~{args.minutes_ago} minutos en el audiolibro")
        else:
            print(f"\n⚠️  Respuesta inesperada: {response.status_code}")
            
    except requests.exceptions.ConnectionError:
        print("\n❌ ERROR: No se pudo conectar a", url)
        print("\nPosibles causas:")
        print("   1. La app Android no está abierta")
        print("   2. Sleep Detection no está activado en Settings")
        print("   3. El teléfono y la PC no están en la misma red WiFi")
        print("   4. El firewall bloquea el puerto 50002")
        print("\nSoluciones:")
        print("   1. Abre la app Android y activa Sleep Detection en Settings")
        print("   2. Cierra y vuelve a abrir la app")
        print("   3. Verifica que ambos dispositivos usan la misma red WiFi")
        print("   4. Usa --ip con la IP correcta del teléfono")
        print(f"\n   Ejemplo: python3 simulate_sleep_detection.py --ip 192.168.1.45")
        
    except requests.exceptions.Timeout:
        print("\n❌ ERROR: Timeout - el servidor no respondió a tiempo")
        
    except Exception as e:
        print(f"\n❌ ERROR: {e}")

if __name__ == "__main__":
    main()
