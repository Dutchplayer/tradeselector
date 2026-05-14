package dutchplayer.tradeselector.util;


import com.google.gson.annotations.SerializedName;
import net.minecraft.core.BlockPos;

/**
 * Simple position data class for storing coordinates
 */
public class Position {
    @SerializedName("x")
    public final double x;
    
    @SerializedName("y") 
    public final double y;
    
    @SerializedName("z")
    public final double z;
    
    public Position(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static Position fromBlockPos(BlockPos pos) {
        return new Position(pos.getX(), pos.getY(), pos.getZ());
    }

    public BlockPos toBlockPos() {
        return BlockPos.containing(x, y, z);
    }
    
    @Override
    public String toString() {
        return String.format("Position{x=%.2f, y=%.2f, z=%.2f}", x, y, z);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Position position = (Position) obj;
        return Double.compare(position.x, x) == 0 &&
               Double.compare(position.y, y) == 0 &&
               Double.compare(position.z, z) == 0;
    }
    
    @Override
    public int hashCode() {
        long bitsX = Double.doubleToLongBits(x);
        long bitsY = Double.doubleToLongBits(y);
        long bitsZ = Double.doubleToLongBits(z);
        return (int) (bitsX ^ (bitsX >>> 32) ^ bitsY ^ (bitsY >>> 32) ^ bitsZ ^ (bitsZ >>> 32));
    }
}
