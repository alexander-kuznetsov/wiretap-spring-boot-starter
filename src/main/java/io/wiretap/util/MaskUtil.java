package io.wiretap.util;

import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.checkdigit.LuhnCheckDigit;

public final class MaskUtil {
    private MaskUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    private static final Pattern PAN_PATTERN = Pattern.compile("(^|\\D)(\\d{13,19})($|\\D)");
    private static final Pattern EXPIRE_DATE_PATTERN = Pattern.compile("(\"\\w+\"\\s?:\\s?\"|<\\w+>|^)(((2[2-9])|([3-4]\\d))((0[1-9])|(1[0-2])))(\"|</\\w+>|$)");
    private static final String[] DEFAULT_EXCLUDE_EXP_DATE_MASKING_TAGS = new String[]{"atmId", "amount", "mcc", "routeBankId", "msgType", "value"};
    private static final Pattern PIN_BLOCK_PATTERN = Pattern.compile("(\"\\w+\"\\s?:\\s?\"|<\\w+>|^)((?=.*[a-fA-F])(?=.*\\d)[A-Fa-f\\d]{16})(\"|</\\w+>|$)");
    private static final String[] DEFAULT_EXCLUDE_PIN_BLOCK_MASKING_TAGS = new String[]{"traceId", "trace_id"};
    private static final Pattern TRACK2_PATTERN = Pattern.compile("(^|\\D)([\\d*]{16,19})(=|u003d|\\\\u003d|d)(\\d{4})(\\d{3})(.*?)($|\\D)"); //553691******7112=****201*************
    private static final Pattern PHONE_PATTERN = Pattern.compile("(^|\\D)(\\+\\d{1,4}?|[78])?(9\\d{2})(\\d{5})(\\d{2})($|\\D)");

    public static String maskAllPans(String formattedMessage, boolean enableLuhnCheck) {
        return replaceAllPans(formattedMessage, possiblePan -> enableLuhnCheck ? checkLuhnAndMaskPan(possiblePan) : simpleCheckAndMaskPan(possiblePan));
    }

    public static String removeAllPans(String formattedMessage) {
        return replaceAllPans(formattedMessage, pan -> "");
    }

    private static String replaceAllPans(String formattedMessage, Function<String, String> panReplacer) {
        if (!StringUtils.isBlank(formattedMessage)) {
            Matcher matcher = PAN_PATTERN.matcher(formattedMessage);

            while (matcher.find()) {
                String possiblePan = matcher.group(2);
                formattedMessage = formattedMessage.replace(
                        possiblePan,
                        panReplacer.apply(possiblePan)
                );
            }

        }
        return formattedMessage;
    }

    public static String maskPhoneNumber(String message) {
        if (!StringUtils.isBlank(message)) {
            Matcher matcher = PHONE_PATTERN.matcher(message);

            final StringBuffer maskedText = new StringBuffer();
            while (matcher.find()) {
                String replacement = matcher.group(1) +
                        Optional.ofNullable(matcher.group(2)).orElse("") +
                        matcher.group(3) +
                        "*****" +
                        matcher.group(5) +
                        matcher.group(6);

                matcher.appendReplacement(maskedText, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(maskedText);
            return maskedText.toString();

        }
        return message;
    }

    public static String maskLog(String text) {
        return maskPhoneNumber(maskAllExpDates(maskAllPinBlocks(maskAllPans(maskPossibleTrack2(text), true))));
    }

    public static String maskIsoField(int fieldNumber, String value) {
        if (StringUtils.isBlank(value)) return value;

        switch (fieldNumber) {
            case 2:
                return MaskUtil.simpleCheckAndMaskPan(value);
            case 14:
            case 52:
            case 48:
            case 125:
                return MaskUtil.maskEverything(value);
            case 35:
                return MaskUtil.maskTrack2(value);
            case 120:
                return MaskUtil.maskPhoneNumber(value);
            default:
                return value;
        }
    }
    public static String maskField(String inputString, String replacement) {
        if (StringUtils.isBlank(inputString)) {
            return inputString;
        } else {
            int valueStart = inputString.lastIndexOf(replacement);
            return StringUtils.overlay(inputString, "*", valueStart, valueStart + replacement.length());
        }
    }

    public static String maskIsoFieldInString(int fieldNumber, String raw, String value) {
        if (StringUtils.isBlank(raw)) return raw;
        int valueStart = raw.lastIndexOf(value);
        return StringUtils.overlay(raw, maskIsoField(fieldNumber, value), valueStart, valueStart + value.length());
    }

    private static String maskPossibleTrack2(String formattedMessage) {
        if (!StringUtils.isBlank(formattedMessage)) {
            Matcher matcher = TRACK2_PATTERN.matcher(formattedMessage);

            while (matcher.find()) {
                String possiblePan = matcher.group(2);
                if (isPan(possiblePan, true)) {
                    String unmaskedTrack2 = matcher.group(2) +
                            matcher.group(3) +
                            matcher.group(4) +
                            matcher.group(5) +
                            matcher.group(6);
                    formattedMessage = formattedMessage.replace(unmaskedTrack2, maskTrack2(unmaskedTrack2));
                }
            }

        }
        return formattedMessage;
    }

    public static String maskTrack2(String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        } else {
            Matcher matcher = TRACK2_PATTERN.matcher(value);
            return matcher.find() ? maskPan(matcher.group(2)) + matcher.group(3) + StringUtils.repeat('*', 4) + matcher.group(5) + StringUtils.repeat('*', matcher.group(6).length()) : value;
        }
    }
    public static String maskAllExpDates(String formattedMessage) {
        if (!StringUtils.isBlank(formattedMessage)) {
            Matcher matcher = EXPIRE_DATE_PATTERN.matcher(formattedMessage);

            while (matcher.find()) {
                if (!StringUtils.containsAny(matcher.group(1), DEFAULT_EXCLUDE_EXP_DATE_MASKING_TAGS)) {
                    formattedMessage = formattedMessage.replace(matcher.group(2), "****");
                }
            }

        }
        return formattedMessage;
    }

    public static String maskAllPinBlocks(String formattedMessage) {
        if (!StringUtils.isBlank(formattedMessage)) {
            Matcher matcher = PIN_BLOCK_PATTERN.matcher(formattedMessage);

            while (matcher.find()) {
                String possiblePinBlock = matcher.group(2);
                if (PIN_BLOCK_PATTERN.matcher(matcher.group(2)).matches() &&
                        !StringUtils.containsAny(matcher.group(1), DEFAULT_EXCLUDE_PIN_BLOCK_MASKING_TAGS)
                ) {
                    formattedMessage = formattedMessage.replace(matcher.group(2), maskEverything(possiblePinBlock));
                }
            }

        }
        return formattedMessage;
    }
    public static String maskEverything(String source) {
        return source != null ? StringUtils.repeat('*', source.length()) : null;
    }
    public static String checkLuhnAndMaskPan(String pan) {
        return isPan(pan, true) ? maskPan(pan) : pan;
    }

    public static String simpleCheckAndMaskPan(String pan) {
        return isPan(pan, false) ? maskPan(pan) : pan;
    }
    private static boolean isPan(String pan, boolean enableLuhnCheck) {
        if (StringUtils.isBlank(pan)) {
            return false;
        }

        boolean lengthOk = pan.length() >= 13 && pan.length() <= 19;
        if (!lengthOk) {
            return false;
        }

        if (enableLuhnCheck) {
            return isLuhnCheckDigitValid(pan);
        }

        return true;
    }

    private static boolean isLuhnCheckDigitValid(String pan) {
        return LuhnCheckDigit.LUHN_CHECK_DIGIT.isValid(pan);
    }

    private static String maskPan(String pan) {
        int index = 0;
        StringBuilder maskedNumber = new StringBuilder();

        for(int i = 0; i < pan.length(); ++i) {
            char c = i < "######******####".length() ? "######******####".charAt(i) : 35;
            if (c == '#') {
                maskedNumber.append(pan.charAt(index));
                ++index;
            } else if (c == '*') {
                maskedNumber.append(c);
                ++index;
            } else {
                maskedNumber.append(c);
            }
        }

        return maskedNumber.toString();
    }

}
