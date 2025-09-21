import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Apply;

@Change(id = "test-author-annotation", order = "002", author = "john.smith")
public class TestAuthorChangeUnit {

    @Apply
    public void execution() {
        System.out.println("Testing author field extraction from annotation");
    }
}