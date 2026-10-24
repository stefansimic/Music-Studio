package dev.rs.slava.icxc.musicstudio.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IssueTest {

    @Test
    void factoriesAndCopies() {
        Path file = Path.of("/music/a.mp3").toAbsolutePath();
        Issue issue = Issue.warning(IssueCode.READ_VALUE_INVALID, "bad").forFile(file).forField(Field.TRACK);
        assertThat(issue.severity()).isEqualTo(Severity.WARNING);
        assertThat(issue.file()).contains(file);
        assertThat(issue.field()).contains(Field.TRACK);
        assertThat(Issue.error(IssueCode.READ_FAILED, "x").severity()).isEqualTo(Severity.ERROR);
        assertThat(Issue.info(IssueCode.READ_FAILED, "x").severity()).isEqualTo(Severity.INFO);
    }

    @Test
    void validation() {
        assertThatThrownBy(() -> Issue.error(IssueCode.READ_FAILED, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Issue.error(null, "x")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Issue(Severity.ERROR, IssueCode.READ_FAILED, "x",
                Optional.of(Path.of("relative")), Optional.empty())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Issue.error(IssueCode.READ_FAILED, "x").forFile(Path.of("rel")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
