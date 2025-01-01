package Observer;

public interface ISubscriber {
    void update(NotificationType type, Object o);
}
