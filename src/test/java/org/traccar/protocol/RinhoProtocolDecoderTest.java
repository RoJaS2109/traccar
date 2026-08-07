package org.traccar.protocol;

import org.junit.jupiter.api.Test;
import org.traccar.ProtocolTest;
import org.traccar.model.Position;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RinhoProtocolDecoderTest extends ProtocolTest {

    @Test
    public void testDecodeRCQ() throws Exception {

        var decoder = inject(new RinhoProtocolDecoder(null));

        // RCQ — Posición periódica (evento 00, IGN ON)
        // Dispositivo en Buenos Aires (-34.60368, -58.38156), 45 km/h, rumbo 180°
        verifyPosition(decoder, text(
                ">RCQ00080726143025-3460368-0583815604518080001260001A2B313050001102115;#0001;ID=KJA-169<"));

        // RCQ — Evento 03 (batería baja), debe generar alarma lowBattery
        verifyPosition(decoder, text(
                ">RCQ03080726150000-3460400-0583820000000080001050001A30013050001002115;#0002;ID=KJA-169<"));

        // RCQ — Evento 04 (SOS/impacto), debe generar alarma sos
        verifyPosition(decoder, text(
                ">RCQ04080726153015-3460500-0583835000000080000980001A35013050001002115;TXT=ALERTA POSIBLE CHOQUE GRAVE FRONTAL;#0003;ID=KJA-169<"));

        // RCQ — Evento 78 (robo/acarreo), debe generar alarma tow
        verifyPosition(decoder, text(
                ">RCQ78080726160030-3460600-058384000850900080001250001A40013050001003115;#0004;ID=KJA-169<"));
    }

    @Test
    public void testDecodeRER() throws Exception {

        var decoder = inject(new RinhoProtocolDecoder(null));

        // RER — Reporte extendido con CAN bus (motor encendido, RPM 1850, temp 89°C)
        verifyPosition(decoder, text(
                ">RER00080726144500-3460368-0583815606009080001300001A2B313050001102115;2010=1850,2012=89,2013=420,4201=72,1020=15230;ID=KJA-169<"));

        // RER — Con CAN bus parcial (algunos valores vacíos con !empty)
        verifyPosition(decoder, text(
                ">RER00080726150000-3460400-058382000450000800001050001A30013050001002115;2010=850,2012!empty,4201=65;ID=KJA-169<"));
    }

    @Test
    public void testDecodeRCR() throws Exception {

        var decoder = inject(new RinhoProtocolDecoder(null));

        // RCR — Evento 13 (puerta abierta), debe generar alarma door
        verifyPosition(decoder, text(
                ">RCR13080726151025-3460368-0583815600000080;#0001;ID=KJA-169;*28<"));

        // RCR — Evento 19 (intrusión), debe generar alarma tampering
        verifyPosition(decoder, text(
                ">RCR19080726152030-3460400-0583820000000080;#0002;ID=KJA-169;*2F<"));
    }

    @Test
    public void testDecodeAlarmMapping() throws Exception {

        var decoder = inject(new RinhoProtocolDecoder(null));

        // Evento 00: sin alarma (posición periódica)
        var pos00 = (Position) decoder.decode(null, null, text(
                ">RCQ00080726143025-3460368-0583815604518080001260001A2B313050001102115;#0001;ID=KJA-169<"));
        assertNotNull(pos00);
        String alarms00 = (String) pos00.getAttributes().get(Position.KEY_ALARM);
        assertTrue(alarms00 == null, "Evento 00 no debería generar alarma: " + alarms00);

        // Evento 03 → lowBattery
        var pos03 = (Position) decoder.decode(null, null, text(
                ">RCQ03080726150000-3460400-0583820000000080001050001A30013050001002115;#0002;ID=KJA-169<"));
        assertNotNull(pos03);
        String alarms03 = (String) pos03.getAttributes().get(Position.KEY_ALARM);
        assertTrue(alarms03 != null && alarms03.contains("lowBattery"),
                "Evento 03 debería generar alarma lowBattery: " + alarms03);

        // Evento 04 → sos
        var pos04 = (Position) decoder.decode(null, null, text(
                ">RCQ04080726153015-3460500-0583835000000080000980001A35013050001002115;#0003;ID=KJA-169<"));
        assertNotNull(pos04);
        String alarms04 = (String) pos04.getAttributes().get(Position.KEY_ALARM);
        assertTrue(alarms04 != null && alarms04.contains("sos"),
                "Evento 04 debería generar alarma sos: " + alarms04);

        // Evento 13 → door
        var pos13 = (Position) decoder.decode(null, null, text(
                ">RCR13080726151025-3460368-0583815600000080;#0001;ID=KJA-169;*28<"));
        assertNotNull(pos13);
        String alarms13 = (String) pos13.getAttributes().get(Position.KEY_ALARM);
        assertTrue(alarms13 != null && alarms13.contains("door"),
                "Evento 13 debería generar alarma door: " + alarms13);

        // Evento 19 → tampering
        var pos19 = (Position) decoder.decode(null, null, text(
                ">RCR19080726152030-3460400-0583820000000080;#0002;ID=KJA-169;*2F<"));
        assertNotNull(pos19);
        String alarms19 = (String) pos19.getAttributes().get(Position.KEY_ALARM);
        assertTrue(alarms19 != null && alarms19.contains("tampering"),
                "Evento 19 debería generar alarma tampering: " + alarms19);

        // Evento 78 → tow
        var pos78 = (Position) decoder.decode(null, null, text(
                ">RCQ78080726160030-3460600-058384000850900080001250001A40013050001003115;#0004;ID=KJA-169<"));
        assertNotNull(pos78);
        String alarms78 = (String) pos78.getAttributes().get(Position.KEY_ALARM);
        assertTrue(alarms78 != null && alarms78.contains("tow"),
                "Evento 78 debería generar alarma tow: " + alarms78);
    }

}
