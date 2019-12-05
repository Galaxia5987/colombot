package robot.subsystems.drivetrain;

import static robot.Constants.Drivetrain.*;
import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.can.TalonSRX;
import com.ctre.phoenix.motorcontrol.can.VictorSPX;
import edu.wpi.first.wpilibj.DoubleSolenoid;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.command.Subsystem;
import robot.Constants;
import robot.Ports;
import robot.Robot;
import robot.subsystems.drivetrain.commands.DriveTypeChooser;

/**
 * This is a temporary subsystem from last year.
 */
public class Drivetrain extends Subsystem {


    private DoubleSolenoid shifter = new DoubleSolenoid(Ports.Drivetrain.shifterUp, Ports.Drivetrain.shifterDown);
    private Timer shiftCounter = new Timer();
    private boolean isShiftingEnabled;
    private TalonSRX leftMaster = new TalonSRX(Ports.Drivetrain.leftMaster);
    private TalonSRX rightMaster = new TalonSRX(Ports.Drivetrain.rightMaster);
    private VictorSPX right1 = new VictorSPX(Ports.Drivetrain.rightSlave1);
    private VictorSPX left1 = new VictorSPX(Ports.Drivetrain.leftSlave1);
    private VictorSPX right2 = new VictorSPX(Ports.Drivetrain.rightSlave2);
    private VictorSPX left2 = new VictorSPX(Ports.Drivetrain.leftSlave2);

    public Drivetrain() {
        leftMaster.setInverted(Ports.Drivetrain.leftMasterReversed);
        left1.setInverted(Ports.Drivetrain.leftSlave1Reversed);
        left2.setInverted(Ports.Drivetrain.leftSlave2Reversed);
        rightMaster.setInverted(Ports.Drivetrain.rightMasterReversed);
        right1.setInverted(Ports.Drivetrain.rightSlave1Reversed);
        right2.setInverted(Ports.Drivetrain.rightSlave2Reversed);

        right1.follow(rightMaster);
        right2.follow(rightMaster);
        left1.follow(leftMaster);
        left2.follow(leftMaster);

        leftMaster.configPeakCurrentLimit(MAX_CURRENT);
        rightMaster.configPeakCurrentLimit(MAX_CURRENT);
    }

    public void setLeftSpeed(double speed) {
        leftMaster.set(ControlMode.PercentOutput, speed);
    }

    public void setRightSpeed(double speed) {
        rightMaster.set(ControlMode.PercentOutput, speed);
    }

    public double getLeftDistance() {
        return convertTicksToDistance(leftMaster.getSelectedSensorPosition());
    }

    public double getRightDistance() {
        return convertTicksToDistance(rightMaster.getSelectedSensorPosition());
    }

    public double getRightVelocity() {
        return convertTicksToDistance(rightMaster.getSelectedSensorVelocity()) * 10;
    }

    public double getLeftVelocity() {
        return convertTicksToDistance(leftMaster.getSelectedSensorVelocity()) * 10;
    }

    public void gearShiftEnabled(boolean enable) {
        isShiftingEnabled = enable;
    }

    public void shift(boolean shiftUp) {
        if (canShift()) {
            if (shiftUp) {
                shifter.set(DoubleSolenoid.Value.kForward);
            } else {
                shifter.set(DoubleSolenoid.Value.kReverse);
            }
            shiftCounter.reset();
        }
    }

    /**
     * This method is meant for automatic shifting
     * first of all it checks if the drivetrain can shift gear
     * than it check if it should shift the gear up or down
     */
    public void autoShift() {
        if (canShift()) {
            if (shiftUp()) {
                shifter.set(DoubleSolenoid.Value.kForward);
            } else if (shiftDown()) {
                shifter.set(DoubleSolenoid.Value.kReverse);
            }
        }
    }


    public int convertDistanceToTicks(double distance) {
        return (int) (distance * TICKS_PER_METER);
    }

    /**
     * because the max input from the joystick is 1 , the joystick input * max velocity is
     * function which represent the relation
     *
     * @param joystickInput the y value from the joystick
     * @return joystick value in m/s
     */
    public double convertJoystickInputToVelocity(double joystickInput) {
        return joystickInput * MAX_VEL;
    }


    /**
     * limit the drivetrain's right side acceleration to a certain acceleration
     *
     * @param desiredVelocity the desired velocity
     * @return the desired velocity if possible, if not the current velocity plus the max acceleration
     */
    public double limitRightAcceleration(double desiredVelocity) {

        //Take the attempted acceleration and see if it is too high.
        if (Math.abs(desiredVelocity - getRightVelocity()) / TIME_STEP >= MAX_ACCELERATION) {
            return getRightVelocity() + MAX_ACCELERATION;
        }

        return desiredVelocity;
    }

    /**
     * limit the drivetrain's left side acceleration to a certain acceleration
     *
     * @param desiredVelocity the desired velocity
     * @return the desired velocity if possible, if not the current velocity plus the max acceleration
     */
    public double limitLeftAcceleration(double desiredVelocity) {

        //Take the attempted acceleration and see if it is too high.
        if (Math.abs((desiredVelocity - getLeftVelocity()) / TIME_STEP) >= MAX_ACCELERATION) {
            return getLeftVelocity() + MAX_ACCELERATION;
        }

        return desiredVelocity;
    }

    public double convertTicksToDistance(int tick) {
        return tick / TICKS_PER_METER;
    }

    public boolean isOnHighGear() {
        return shifter.get() == DoubleSolenoid.Value.kForward;
    }

    /**
     * This method return if the robot hasn't shifted recently or turn
     * to check if the robot can shift gear
     *
     * @return if the drivetrain can shift gear
     */
    public boolean canShift() {
        return (Math.abs(getLeftVelocity() - getRightVelocity()) < DIFFERENTIAL_TOLERANCE) && (shiftCounter.get() > MIN_SHIFT_TIME) && isShiftingEnabled;
    }

    /**
     * This method check if the acceleration is higher than the minimal acceleration for gear shifting
     * and if the velocity is higher than the minimal velocity for shifting
     *
     * @return if the robot should shift the gear up
     */
    public boolean shiftUp() {
        return (Robot.navx.getRawAccelX() > SHIFT_UP_ACCELERATION) && ((getRightVelocity() + getLeftVelocity()) / 2 > SHIFT_UP_POINT && !isOnHighGear());
    }

    /**
     * This method check if the acceleration is lower than the minimal acceleration for gear shifting
     * and if the velocity is lower than the minimal velocity for shifting
     *
     * @return if the robot should shift the gear down
     */
    public boolean shiftDown() {
        return (Robot.navx.getRawAccelX() < SHIFT_DOWN_ACCELERATION) && ((getRightVelocity() + getLeftVelocity()) / 2 < SHIFT_DOWN_POINT && isOnHighGear());
    }

    @Override
    protected void initDefaultCommand() {
        setDefaultCommand(new DriveTypeChooser());
    }
}
