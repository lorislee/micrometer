package io.micrometer.core.instrument;


import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.stream.Stream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * Tests for {@link Tags}.
 *
 * @author Phillip Webb
 */
public class FixmeTagsTest {

    // FIXME convert to Jupiter

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void andKeyValueShouldReturnNewInstanceWithAddedTags() throws Exception {
        Tags source = Tags.of("t1", "v1");
        Tags merged = source.and("t2","v2");
        assertThat(source).isNotSameAs(merged);
        assertTags(source, "t1", "v1");
        assertTags(merged, "t1", "v1", "t2", "v2");
    }

    @Test
    public void andKeyValuesShouldReturnNewInstanceWithAddedTags() throws Exception {
        Tags source = Tags.of("t1", "v1");
        Tags merged = source.and("t2","v2", "t3", "v3");
        assertThat(source).isNotSameAs(merged);
        assertTags(source, "t1", "v1");
        assertTags(merged, "t1", "v1", "t2", "v2", "t3", "v3");
    }

    @Test
    public void andKeyValuesWhenKeyValuesAreOddShouldThrowException() throws Exception {
        thrown.expect(IllegalArgumentException.class);
        Tags.empty().and("t1", "v1", "t2");
    }

    @Test
    public void andKeyValuesWhenKeyValuesAreEmptyShouldReturnCurrentInstance() throws Exception {
        Tags source = Tags.of("t1", "v1");
        Tags merged = source.and(new String[0]);
        assertThat(source).isSameAs(merged);
    }

    @Test
    public void andKeyValuesWhenKeyValuesAreNullShouldReturnCurrentInstance() throws Exception {
        Tags source = Tags.of("t1", "v1");
        Tags merged = source.and((String[]) null);
        assertThat(source).isSameAs(merged);
    }

    @Test
    public void andTagsShouldReturnANewInstanceWithTags() throws Exception {
        Tags source = Tags.of("t1", "v1");
        Tags merged = source.and(Tag.of("t2","v2"));
        assertThat(source).isNotSameAs(merged);
        assertTags(source, "t1", "v1");
        assertTags(merged, "t1", "v1", "t2", "v2");
    }

    @Test
    public void andTagsWhenTagsAreEmptyShouldReturnCurrentInstance() throws Exception {
        Tags source = Tags.of("t1", "v1");
        Tags merged = source.and((Tag[]) null);
        assertThat(source).isSameAs(merged);
    }

    @Test
    public void andIterableShouldReturnNewInstanceWithTags() throws Exception {
        Tags source = Tags.of("t1", "v1");
        Tags merged = source.and(Collections.singleton(Tag.of("t2","v2")));
        assertThat(source).isNotSameAs(merged);
        assertTags(source, "t1", "v1");
        assertTags(merged, "t1", "v1", "t2", "v2");
    }

    @Test
    public void andIterableWhenIterableIsNullShouldReturnCurrentInstance() throws Exception {
        Tags source = Tags.of("t1", "v1");
        Tags merged = source.and((Iterable<Tag>) null);
        assertThat(source).isSameAs(merged);
    }

    @Test
    public void andWhenAlreadyContainsKeyShouldReplaceValue() throws Exception {
        Tags source = Tags.of("t1", "v1");
        Tags merged = source.and("t2","v2", "t1", "v3");
        assertThat(source).isNotSameAs(merged);
        assertTags(source, "t1", "v1");
        assertTags(merged, "t1", "v3", "t2", "v2");
    }

    @Test
    public void iteratorShouldIterateTags() throws Exception {
        Tags tags = Tags.of("t1", "v1");
        Iterator<Tag> iterator = tags.iterator();
        assertThat(iterator).containsExactly(Tag.of("t1", "v1"));
    }

    @Test
    public void streamShouldStreamTags() throws Exception {
        Tags tags = Tags.of("t1", "v1");
        Stream<Tag> iterator = tags.stream();
        assertThat(iterator).containsExactly(Tag.of("t1", "v1"));
    }

    @Test
    public void concatIterableShouldReturnNewInstanceWithAddedTags() throws Exception {
        Tags source = Tags.of("t1", "v1");
        Tags merged = Tags.concat(source, Collections.singleton(Tag.of("t2","v2")));
        assertThat(source).isNotSameAs(merged);
        assertTags(source, "t1", "v1");
        assertTags(merged, "t1", "v1", "t2", "v2");
    }

    @Test
    public void concatStringsShouldReturnNewInstanceWithAddedTags() throws Exception {
        Tags source = Tags.of("t1", "v1");
        Tags merged = Tags.concat(source, "t2","v2");
        assertThat(source).isNotSameAs(merged);
        assertTags(source, "t1", "v1");
        assertTags(merged, "t1", "v1", "t2", "v2");
    }

    @Test
    @Deprecated
    public void zipShouldReturnNewInstanceWithTags() throws Exception {
        Tags tags = Tags.zip("t1", "v1", "t2", "v2");
        assertTags(tags, "t1", "v1", "t2", "v2");
    }

    @Test
    public void ofIterableShouldReturnNewInstanceWithTags() throws Exception {
        Tags tags = Tags.of(Collections.singleton(Tag.of("t1", "v1")));
        assertTags(tags, "t1", "v1");
    }

    @Test
    public void ofIterableWhenIterableIsTagsShouldReturnSameInstance() throws Exception {
        Tags source = Tags.of("t1", "v1");
        Tags tags = Tags.of(source);
        assertThat(tags).isSameAs(source);
    }

    @Test
    public void ofKeyValueShouldReturnNewInstance() throws Exception {
        Tags tags = Tags.of("t1", "v1");
        assertTags(tags, "t1", "v1");
    }

    @Test
    public void ofKeyValuesShouldReturnNewInstance() throws Exception {
        Tags tags = Tags.of("t1", "v1", "t2", "v2");
        assertTags(tags, "t1", "v1", "t2", "v2");
    }

    @Test
    public void emptyShouldNotContainTags() throws Exception {
        assertThat(Tags.empty().iterator()).isEmpty();
    }

    private void assertTags(Tags tags, String... keyValues) {
        Iterator<Tag> actual = tags.iterator();
        Iterator<String> expected = Arrays.asList(keyValues).iterator();
        while(actual.hasNext()) {
            Tag tag = actual.next();
            assertThat(tag.getKey()).isEqualTo(expected.next());
            assertThat(tag.getValue()).isEqualTo(expected.next());
        }
        assertThat(expected.hasNext()).isFalse();
    }
}
