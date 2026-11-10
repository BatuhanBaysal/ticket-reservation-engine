package com.ticket.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MessageTypesTest {

    @Test
    void definesThirteenDistinctTypeNames() {
        List<String> names = ConstantReader.stringConstantsOf(MessageTypes.class);

        assertThat(names).hasSize(13).doesNotHaveDuplicates();
    }

    @Test
    void typeNamesAreNotBlank() {
        List<String> names = ConstantReader.stringConstantsOf(MessageTypes.class);

        assertThat(names).isNotEmpty().allSatisfy(name -> assertThat(name).isNotBlank());
    }
}
