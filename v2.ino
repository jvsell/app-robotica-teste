#include <SoftwareSerial.h>
SoftwareSerial btSerial(10, 11); // RX, TX do módulo Bluetooth
#define IN1 3 // Motor Esquerdo - Frente
#define IN2 5 // Motor Esquerdo - Trás
#define IN3 6 // Motor Direito - Frente
#define IN4 9 // Motor Direito - Trás
#define LED 13 // LED indicador
unsigned long ultimoComando = 0;
const unsigned long tempoDesconexao = 1500;
bool emMovimento = false;
void moverMotores(bool in1, bool in2, bool in3, bool in4,
 int pwmEsquerdo, int pwmDireito);
void setup() {
 btSerial.begin(9600);
 Serial.begin(9600);
 pinMode(IN1, OUTPUT);
 pinMode(IN2, OUTPUT);
 pinMode(IN3, OUTPUT);
 pinMode(IN4, OUTPUT);
 pinMode(LED, OUTPUT);
} 
void loop() {
 if (btSerial.available()) {
 String mensagem = btSerial.readStringUntil('\n');
 Serial.println(mensagem);
 digitalWrite(LED, HIGH);
 ultimoComando = millis();
 int fIndex = mensagem.indexOf('F');
 int tIndex = mensagem.indexOf('T');
 int dIndex = mensagem.indexOf('D');
 int eIndex = mensagem.indexOf('E');
 if (fIndex != -1 && tIndex != -1 && dIndex != -1 && eIndex != -1) {

 int f = mensagem.substring(fIndex + 1, tIndex).toInt();
 int t = mensagem.substring(tIndex + 1, dIndex).toInt();
 int d = mensagem.substring(dIndex + 1, eIndex).toInt();
 int e = mensagem.substring(eIndex + 1).toInt();
 int velocidadeMaxima = max(max(f, t), max(d, e));
 int sentido = (f >= t) ? 1 : -1;

 int velocidadeEsquerdo = (f - t) + (d - e) * sentido;
 int velocidadeDireito = (f - t) + (e - d) * sentido;
 bool in1, in2;
 int pwmEsquerdo;

 if (velocidadeEsquerdo < 0) { in1 = 1; } else { in1 = 0; }
 if (velocidadeEsquerdo > 0) { in2 = 1; } else { in2 = 0; }

 pwmEsquerdo = min(abs(velocidadeEsquerdo), velocidadeMaxima);
 bool in3, in4;
 int pwmDireito;

 if (velocidadeDireito < 0) { in3 = 1; } else { in3 = 0; }
 if (velocidadeDireito > 0) { in4 = 1; } else { in4 = 0; }
 pwmDireito = min(abs(velocidadeDireito), velocidadeMaxima);
 // Aciona os motores com os valores calculados
 moverMotores(in1, in2, in3, in4, pwmEsquerdo, pwmDireito);
 }
 }

 // Desliga os motores se perder conexão
 if (millis() - ultimoComando > tempoDesconexao && emMovimento) {
 moverMotores(0, 0, 0, 0, 0, 0);
 digitalWrite(LED, LOW);
 emMovimento = false;
 }
} 
void moverMotores(bool in1, bool in2, bool in3, bool in4,
 int pwmEsquerdo, int pwmDireito) {
 // Aplica PWM no pino ativo, ou desliga se não estiver ativo
 analogWrite(IN1, in1 ? pwmEsquerdo : 0); // Motor esquerdo para trás
 analogWrite(IN2, in2 ? pwmEsquerdo : 0); // Motor esquerdo para frente
 analogWrite(IN3, in3 ? pwmDireito : 0); // Motor direito para trás
 analogWrite(IN4, in4 ? pwmDireito : 0); // Motor direito para frente
 // Atualiza o estado de movimento
 emMovimento = (pwmEsquerdo > 0 || pwmDireito > 0);
}