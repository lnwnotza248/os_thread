/** Cooperative lifecycle control for workers managed by DynamicWorkerPool. */
public interface WorkerControl {
    boolean isIdle();

    void requestStop();
}
