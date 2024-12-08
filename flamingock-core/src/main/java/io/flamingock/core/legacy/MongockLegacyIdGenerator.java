package io.flamingock.core.legacy;

import io.flamingock.commons.utils.StringUtil;

public class MongockLegacyIdGenerator {

    private MongockLegacyIdGenerator() {
    }

    public static String getNewId(String legacyId, String author) {
        if(StringUtil.isEmpty(author)) {
            return legacyId;
        } else {
            return String.format("[%s]%s", author, legacyId);
        }
    }
}
