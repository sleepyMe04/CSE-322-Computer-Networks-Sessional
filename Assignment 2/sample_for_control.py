import paho.mqtt.client as mqtt

broker = "broker.hivemq.com"
topic = "buet/cse/2105114/led"

client = mqtt.Client()
client.connect(broker)

print("LED Controller")
print("Press 'y' to turn ON, 'n' to turn OFF, 'q' to quit.")

while True:
    key = input("Enter command (y/n/q): ").lower()
    if key == 'y':
        client.publish(topic, "on")
        print("Sent: on")
    elif key == 'n':
        client.publish(topic, "off")
        print("Sent: off")
    elif key == 'q':
        print("Exiting...")
        break
    else:
        print("Invalid input. Use 'y', 'n', or 'q'.")

client.disconnect()