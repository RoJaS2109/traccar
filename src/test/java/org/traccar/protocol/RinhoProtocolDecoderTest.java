package org.traccar.protocol;

import org.junit.jupiter.api.Test;
import org.traccar.ProtocolTest;
import org.traccar.model.Position;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                ">RCR13080726151025-3460368-0583815600000080;#0001;ID=KJA-169;*16<"));

        // RCR — Evento 19 (intrusión), debe generar alarma tampering
        verifyPosition(decoder, text(
                ">RCR19080726152030-3460400-0583820000000080;#0002;ID=KJA-169;*11<"));
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
                ">RCR13080726151025-3460368-0583815600000080;#0001;ID=KJA-169;*16<"));
        assertNotNull(pos13);
        String alarms13 = (String) pos13.getAttributes().get(Position.KEY_ALARM);
        assertTrue(alarms13 != null && alarms13.contains("door"),
                "Evento 13 debería generar alarma door: " + alarms13);

        // Evento 19 → tampering
        var pos19 = (Position) decoder.decode(null, null, text(
                ">RCR19080726152030-3460400-0583820000000080;#0002;ID=KJA-169;*11<"));
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

    @Test
    public void testDecodeRCW() throws Exception {

        var decoder = inject(new RinhoProtocolDecoder(null));

        // RCW — Reporte Compacto WiFi (course antes de speed, odómetro decimal)
        var position = (Position) decoder.decode(null, null, text(
                ">RCW00070826204659-3869515-0623511310800011200003000009900000000007F;ID=KJA-169;*4B<"));

        assertNotNull(position, "El decoder debería parsear RCW correctamente");

        // Fecha: 07/08/2026 20:46:59 UTC
        var cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        cal.setTime(position.getFixTime());
        assertEquals(2026, cal.get(java.util.Calendar.YEAR));
        assertEquals(7, cal.get(java.util.Calendar.DAY_OF_MONTH));
        assertEquals(8, cal.get(java.util.Calendar.MONTH) + 1);
        assertEquals(20, cal.get(java.util.Calendar.HOUR_OF_DAY));
        assertEquals(46, cal.get(java.util.Calendar.MINUTE));
        assertEquals(59, cal.get(java.util.Calendar.SECOND));

        // Coordenadas DEG_DEG: -3869515 → -38.69515, -06235113 → -62.35113
        assertEquals(-38.69515, position.getLatitude(), 0.00001);
        assertEquals(-62.35113, position.getLongitude(), 0.00001);

        // Course 108°, Speed 0 km/h → 0 nudos
        assertEquals(108.0, position.getCourse(), 0.1);
        assertEquals(0.0, position.getSpeed(), 0.1);

        // GPS: power=1 (ON), satellites=12, fix=3D, pdop=0, age=0
        assertEquals(1, ((Number) position.getAttributes().get(Position.KEY_GPS)).intValue());
        assertEquals(12, ((Number) position.getAttributes().get(Position.KEY_SATELLITES)).intValue());
        assertEquals(0, ((Number) position.getAttributes().get(Position.KEY_PDOP)).intValue());

        // GSM: modem=0, networkType=0, csq=99 (sin señal)
        assertEquals(0, ((Number) position.getAttributes().get("modemPower")).intValue());
        assertEquals(0, ((Number) position.getAttributes().get("networkType")).intValue());
        assertEquals(99, ((Number) position.getAttributes().get(Position.KEY_RSSI)).intValue());

        // Odómetro decimal: 0 metros (campo de 10 dígitos: 0000000000)
        assertEquals(0L, ((Number) position.getAttributes().get(Position.KEY_ODOMETER)).longValue());

        // IGN+IN flags: 0x7F → IGN=0 (bit 7), IN0-6=1 (bits 0-6)
        assertEquals(0x7F, ((Number) position.getAttributes().get(Position.KEY_INPUT)).intValue());

        // Position should be valid
        assertTrue(position.getValid());

        System.out.println("✅ RCW decodificado correctamente:");
        System.out.println("  Fecha:       " + position.getFixTime());
        System.out.println("  Lat:         " + position.getLatitude());
        System.out.println("  Lon:         " + position.getLongitude());
        System.out.println("  Course:      " + position.getCourse() + "°");
        System.out.println("  Speed:       " + position.getSpeed() + " kn");
        System.out.println("  GPS:         ON, " + position.getAttributes().get(Position.KEY_SATELLITES) + " sats, 3D fix");
        System.out.println("  NetworkType: " + position.getAttributes().get("networkType"));
        System.out.println("  Odómetro:    " + position.getAttributes().get(Position.KEY_ODOMETER) + " m");
        System.out.println("  RSSI:        " + position.getAttributes().get(Position.KEY_RSSI));
        System.out.println("  IGN+IN:      0x" + Integer.toHexString(((Number) position.getAttributes().get(Position.KEY_INPUT)).intValue()));
        System.out.println("  Válido:      " + position.getValid());
    }

    @Test
    public void testDecodeRGP() throws Exception {

        var decoder = inject(new RinhoProtocolDecoder(null));

        // RGP — Reporte GPS (estándar simplificado)
        var position = (Position) decoder.decode(null, null, text(
                ">RGP070826204709-3869514-062351120001083007F0000;ID=KJA-169;*4E<"));

        assertNotNull(position, "El decoder debería parsear RGP correctamente");

        // Fecha: 07/08/2026 20:47:09 UTC
        var cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        cal.setTime(position.getFixTime());
        assertEquals(2026, cal.get(java.util.Calendar.YEAR));
        assertEquals(7, cal.get(java.util.Calendar.DAY_OF_MONTH));
        assertEquals(8, cal.get(java.util.Calendar.MONTH) + 1);
        assertEquals(20, cal.get(java.util.Calendar.HOUR_OF_DAY));
        assertEquals(47, cal.get(java.util.Calendar.MINUTE));
        assertEquals(9, cal.get(java.util.Calendar.SECOND));

        // Coordenadas: -38.69514, -62.35112
        assertEquals(-38.69514, position.getLatitude(), 0.00001);
        assertEquals(-62.35112, position.getLongitude(), 0.00001);

        // Speed 0, Course 108°
        assertEquals(0.0, position.getSpeed(), 0.1);
        assertEquals(108.0, position.getCourse(), 0.1);

        // GPS: fix=3D, age=0
        assertEquals(3, ((Number) position.getAttributes().get("gpsFix")).intValue());
        assertEquals(0, ((Number) position.getAttributes().get("gpsAge")).intValue());

        // IGN+IN: 0x7F → IGN=0, IN0-6=1
        assertEquals(0x7F, ((Number) position.getAttributes().get(Position.KEY_INPUT)).intValue());

        // PDOP=0, valid
        assertEquals(0, ((Number) position.getAttributes().get(Position.KEY_PDOP)).intValue());
        assertTrue(position.getValid());

        System.out.println("✅ RGP decodificado correctamente:");
        System.out.println("  Fecha:   " + position.getFixTime());
        System.out.println("  Lat:     " + position.getLatitude());
        System.out.println("  Lon:     " + position.getLongitude());
        System.out.println("  Course:  " + position.getCourse() + "°");
        System.out.println("  Speed:   " + position.getSpeed() + " kn");
        System.out.println("  GPS Fix: " + position.getAttributes().get("gpsFix") + "D, age=" + position.getAttributes().get("gpsAge") + "s");
        System.out.println("  IGN+IN:  0x" + Integer.toHexString(((Number) position.getAttributes().get(Position.KEY_INPUT)).intValue()));
        System.out.println("  Válido:  " + position.getValid());
    }

    @Test
    public void testDecodeRCY() throws Exception {

        var decoder = inject(new RinhoProtocolDecoder(null));

        // RCY — Standard con Altitud
        var position = (Position) decoder.decode(null, null, text(
                ">RCY00070826204722-3869514-06235112000108-000012;IGN0;IN7F;XP00;ID=KJA-169;*13<"));

        assertNotNull(position, "El decoder debería parsear RCY correctamente");

        // Fecha: 07/08/2026 20:47:22 UTC
        var cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        cal.setTime(position.getFixTime());
        assertEquals(2026, cal.get(java.util.Calendar.YEAR));
        assertEquals(7, cal.get(java.util.Calendar.DAY_OF_MONTH));
        assertEquals(8, cal.get(java.util.Calendar.MONTH) + 1);
        assertEquals(20, cal.get(java.util.Calendar.HOUR_OF_DAY));
        assertEquals(47, cal.get(java.util.Calendar.MINUTE));
        assertEquals(22, cal.get(java.util.Calendar.SECOND));

        // Coordenadas: -38.69514, -62.35112
        assertEquals(-38.69514, position.getLatitude(), 0.00001);
        assertEquals(-62.35112, position.getLongitude(), 0.00001);

        // Speed 0, Course 108°, Altitude 0
        assertEquals(0.0, position.getSpeed(), 0.1);
        assertEquals(108.0, position.getCourse(), 0.1);
        assertEquals(0.0, position.getAltitude(), 0.1);

        // IGN=0, IN=0x7F, XP=0
        assertEquals(0x7F, ((Number) position.getAttributes().get(Position.KEY_INPUT)).intValue());
        assertEquals(0, ((Number) position.getAttributes().get(Position.KEY_OUTPUT)).intValue());

        // Valid (GPS1=1, GPS2=2)
        assertTrue(position.getValid());

        System.out.println("✅ RCY decodificado correctamente:");
        System.out.println("  Fecha:   " + position.getFixTime());
        System.out.println("  Lat:     " + position.getLatitude());
        System.out.println("  Lon:     " + position.getLongitude());
        System.out.println("  Course:  " + position.getCourse() + "°");
        System.out.println("  Speed:   " + position.getSpeed() + " kn");
        System.out.println("  Alt:     " + position.getAltitude() + " m");
        System.out.println("  IGN+IN:  0x" + Integer.toHexString(((Number) position.getAttributes().get(Position.KEY_INPUT)).intValue()));
        System.out.println("  Válido:  " + position.getValid());
    }

    @Test
    public void testDecodeRCRExtended() throws Exception {

        var decoder = inject(new RinhoProtocolDecoder(null));

        // RCR extendido (con GPS/GSM + odómetro en el sufijo)
        var position = (Position) decoder.decode(null, null, text(
                ">RCR00070826204738-3869514-062351130001087F000000000000013001200000099+0000FF;ID=KJA-169;*62<"));

        assertNotNull(position, "El decoder debería parsear RCR extendido correctamente");

        // Fecha: 07/08/2026 20:47:38 UTC (DD/MM/YY en RCR)
        var cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        cal.setTime(position.getFixTime());
        assertEquals(2026, cal.get(java.util.Calendar.YEAR));
        assertEquals(7, cal.get(java.util.Calendar.DAY_OF_MONTH));
        assertEquals(8, cal.get(java.util.Calendar.MONTH) + 1); // August
        assertEquals(20, cal.get(java.util.Calendar.HOUR_OF_DAY));
        assertEquals(47, cal.get(java.util.Calendar.MINUTE));
        assertEquals(38, cal.get(java.util.Calendar.SECOND));

        // Coordenadas: -38.69514, -62.35113
        assertEquals(-38.69514, position.getLatitude(), 0.00001);
        assertEquals(-62.35113, position.getLongitude(), 0.00001);

        // Speed 0, Course 108°
        assertEquals(0.0, position.getSpeed(), 0.1);
        assertEquals(108.0, position.getCourse(), 0.1);

        // IO flags: 0x7F
        assertEquals(0x7F, ((Number) position.getAttributes().get(Position.KEY_INPUT)).intValue());
        assertTrue(position.getValid());

        // Sin alarma (evento 00)
        assertEquals(0, ((Number) position.getAttributes().get(Position.KEY_EVENT)).intValue());

        System.out.println("✅ RCR extendido decodificado:");
        System.out.println("  Fecha:   " + position.getFixTime());
        System.out.println("  Lat:     " + position.getLatitude());
        System.out.println("  Lon:     " + position.getLongitude());
        System.out.println("  Course:  " + position.getCourse() + "°");
        System.out.println("  Speed:   " + position.getSpeed() + " kn");
        System.out.println("  IO:      0x" + Integer.toHexString(((Number) position.getAttributes().get(Position.KEY_INPUT)).intValue()));
        System.out.println("  Válido:  " + position.getValid());
    }

}
