package jatrailmap;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;

public interface SessionRepository extends MongoRepository<Session, String> {
    Session insert(Session session);
    List<Session> findByToken(String token);
    void deleteByToken(String token);
}
