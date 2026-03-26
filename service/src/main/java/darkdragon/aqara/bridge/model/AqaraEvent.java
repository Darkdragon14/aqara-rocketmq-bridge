package darkdragon.aqara.bridge.model;

public record AqaraEvent(
        String type,
        String subjectId,
        String resourceId,
        String value,
        long time,
        int statusCode,
        TriggerSource triggerSource,
        String attach,
        String msgId,
        String openId
) {

    public record TriggerSource(
            Integer type,
            Long time,
            String id
    ) {
    }
}
