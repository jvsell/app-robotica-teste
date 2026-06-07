#include <SoftwareSerial.h>
// Pinos usados para o Bluetooth HC-06
#define RX_BT 10 // RX do Arduino (ligado no TX do HC-06)
#define TX_BT 11 // TX do Arduino (ligado no RX do HC-06)
// Cria a comunicação com o Bluetooth
SoftwareSerial btSerial(RX_BT, TX_BT);
void setup() {
 Serial.begin(9600); // Velocidade de comunicação entre Arduino e computador
 btSerial.begin(9600); // Velocidade de comunicação entre Arduino e Bluetooth
}
void loop() {
 // Verifica se chegou alguma mensagem pelo Bluetooth
 if (btSerial.available()) {
 // Lê o texto enviado pelo Bluetooth até o final da mensagem
 String mensagem = btSerial.readStringUntil('\n');
 // Mostra a mensagem no computador
 Serial.println(mensagem);
 }
}