package io.github.zancrow321.jadm.engine.ai.windbot;

import io.github.zancrow321.jadm.engine.data.CardInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * WindBot's {@code DefaultExecutor}: battle and chain heuristics shared by every deck, plus ready-made rules for
 * common staples that decks can register.
 */
public abstract class DefaultExecutor extends Executor {
    protected static final class _CardId {
        public static final int JizukirutheStarDestroyingKaiju = 63941210;
        public static final int ThunderKingtheLightningstrikeKaiju = 48770333;
        public static final int DogorantheMadFlameKaiju = 93332803;
        public static final int RadiantheMultidimensionalKaiju = 28674152;
        public static final int GadarlatheMysteryDustKaiju = 36956512;
        public static final int KumongoustheStickyStringKaiju = 29726552;
        public static final int GamecieltheSeaTurtleKaiju = 55063751;
        public static final int SuperAntiKaijuWarMachineMechaDogoran = 84769941;

        public static final int SandaionTheTimelord = 33015627;
        public static final int GabrionTheTimelord = 6616912;
        public static final int MichionTheTimelord = 7733560;
        public static final int ZaphionTheTimelord = 28929131;
        public static final int HailonTheTimelord = 34137269;
        public static final int RaphionTheTimelord = 60222213;
        public static final int SadionTheTimelord = 65314286;
        public static final int MetaionTheTimelord = 74530899;
        public static final int KamionTheTimelord = 91712985;
        public static final int LazionTheTimelord = 92435533;

        public static final int LeftArmofTheForbiddenOne = 7902349;
        public static final int RightLegofTheForbiddenOne = 8124921;
        public static final int LeftLegofTheForbiddenOne = 44519536;
        public static final int RightArmofTheForbiddenOne = 70903634;
        public static final int ExodiaTheForbiddenOne = 33396948;

        public static final int UltimateConductorTytanno = 18940556;
        public static final int ElShaddollConstruct = 20366274;
        public static final int AllyOfJusticeCatastor = 26593852;

        public static final int DupeFrog = 46239604;
        public static final int MaraudingCaptain = 2460565;

        public static final int BlackRoseDragon = 73580471;
        public static final int JudgmentDragon = 57774843;
        public static final int TopologicTrisbaena = 72529749;
        public static final int EvilswarmExcitonKnight = 46772449;
        public static final int HarpiesFeatherDuster = 18144506;
        public static final int DarkMagicAttack = 2314238;
        public static final int MysticalSpaceTyphoon = 5318639;
        public static final int CosmicCyclone = 8267140;
        public static final int GalaxyCyclone = 5133471;
        public static final int BookOfMoon = 14087893;
        public static final int BottomlessTrapHole = 29401950;
        public static final int CompulsoryEvacuationDevice = 94192409;
        public static final int EnemyController = 98045062;
        public static final int DimensionalPrison = 70342110;
        public static final int RingOfDestruction = 83555666;
        public static final int RaigekiBreak = 4178474;
        public static final int KarmaCut = 71587526;
        public static final int SakuretsuArmor = 56120475;
        public static final int Crackdown = 36975314;
        public static final int DogmatikaPunishment = 82956214;
        public static final int DogmatikaAlbaZoa = 51522296;
        public static final int PaleozoicDinomischus = 38761908;
        public static final int DracobackTheDragonSteed = 38745520;
        public static final int PhoenixWingWindBlast = 63356631;
        public static final int QuakingMirrorForce = 40838625;
        public static final int DrowningMirrorForce = 47475363;
        public static final int BlazingMirrorForce = 75249652;
        public static final int StormingMirrorForce = 5650082;
        public static final int MirrorForce = 44095762;
        public static final int DarkMirrorForce = 20522190;
        public static final int CallOfTheHaunted = 97077563;
        public static final int Scapegoat = 73915051;
        public static final int BreakthroughSkill = 78474168;
        public static final int SolemnJudgment = 41420027;
        public static final int SolemnWarning = 84749824;
        public static final int SolemnStrike = 40605147;
        public static final int TreebornFrog = 12538374;
        public static final int TorrentialTribute = 53582587;
        public static final int EvenlyMatched = 15693423;
        public static final int HeavyStorm = 19613556;
        public static final int HammerShot = 26412047;
        public static final int DarkHole = 53129443;
        public static final int Raigeki = 12580477;
        public static final int SmashingGround = 97169186;
        public static final int PotOfDesires = 35261759;
        public static final int AllureofDarkness = 1475311;
        public static final int DimensionalBarrier = 83326048;
        public static final int InterruptedKaijuSlumber = 99330325;

        public static final int ChickenGame = 67616300;
        public static final int SantaClaws = 46565218;

        public static final int CastelTheSkyblasterMusketeer = 82633039;
        public static final int CrystalWingSynchroDragon = 50954680;
        public static final int NumberS39UtopiaTheLightning = 56832966;
        public static final int Number39Utopia = 84013237;
        public static final int UltimayaTzolkin = 1686814;
        public static final int MekkKnightCrusadiaAstram = 21887175;
        public static final int HamonLordofStrikingThunder = 32491822;
        public static final int UriaLordofSearingFlames = 6007213;
        public static final int RavielLordofPhantasms = 69890967;

        public static final int MoonMirrorShield = 19508728;
        public static final int PhantomKnightsFogBlade = 25542642;

        public static final int VampireFraeulein = 6039967;
        public static final int InjectionFairyLily = 79575620;

        public static final int BlueEyesChaosMAXDragon = 55410871;

        public static final int AshBlossom = 14558127;
        public static final int MaxxC = 23434538;
        public static final int LockBird = 94145021;
        public static final int GhostOgreAndSnowRabbit = 59438930;
        public static final int GhostBelle = 73642296;
        public static final int EffectVeiler = 97268402;
        public static final int GhostMournerMoonlitChill = 52038441;
        public static final int ArtifactLancea = 34267821;
        public static final int DimensionShifter = 91800273;
        public static final int NibiruThePrimalBeing = 27204311;
        public static final int MulcharmyPurulia = 84192580;
        public static final int MulcharmyFuwalos = 42141493;
        public static final int MulcharmyNyalus = 87126721;

        public static final int CalledByTheGrave = 24224830;
        public static final int CrossoutDesignator = 65681983;
        public static final int InfiniteImpermanence = 10045474;
        public static final int GalaxySoldier = 46659709;
        public static final int MacroCosmos = 30241314;
        public static final int UpstartGoblin = 70368879;
        public static final int CyberEmergency = 60600126;
        public static final int TheAgentOfCreationVenus = 64734921;

        public static final int EaterOfMillions = 63845230;

        public static final int InvokedPurgatrio = 12307878;
        public static final int ChaosAncientGearGiant = 51788412;
        public static final int UltimateAncientGearGolem = 12652643;

        public static final int RedDragonArchfiend = 70902743;

        public static final int ImperialOrder = 61740673;
        public static final int ImperialIronWall = 30459350;
        public static final int RoyalDecreel = 51452091;
        public static final int NaturalExterio = 99916754;
        public static final int NaturiaBeast = 33198837;
        public static final int SwordsmanLV7 = 37267041;
        public static final int AntiSpellFragrance = 58921041;
        public static final int Number41BagooskatheTerriblyTiredTapir = 90590303;
        public static final int SkillDrain = 82732705;

        public static final int DimensionalFissure = 81674782;
        public static final int BanisheroftheRadiance = 94853057;
        public static final int BanisheroftheLight = 61528025;
        public static final int KashtiraAriseHeart = 48626373;
        public static final int MaskedHERODarkLaw = 58481572;
        public static final int DifferentDimensionGround = 31849106;
        public static final int RetaliatingC = 46502744;
        public static final int AmorphageGoliath = 69072185;
        public static final int AntihumanIntelligenceMEPSYYA = 58844135;
        public static final int HeraldoftheArcLight = 79606837;
        public static final int RyuGeRealmWyrmWinds = 55154344;
        public static final int FAHangOnMach = 93449450;
        public static final int AwakeningoftheSacredBeasts = 53701259;

        public static final int VaylantzWorld_ShinraBansho = 49568943;
        public static final int VaylantzWorld_KonigWissen = 75952542;
        public static final int DivineArsenalAAZEUS_SkyThunder = 90448279;
        public static final int LightningStorm = 14532163;
        public static final int MistakenArrest = 4227096;
        public static final int ThunderKingRaiOh = 71564252;
        public static final int ThunderDragonColossus = 15291624;
        public static final int DeckLockdown = 1149109;
        public static final int DoomZDestruction = 80320877;
        public static final int Mistake = 59305593;
        public static final int MirrorBarrier = 95915457;

        public static final int BelialMarquisOfDarkness = 33655493;
        public static final int ChirubiméPrincessOfAutumnLeaves = 87294988;
        public static final int PerformapalBarokuriboh = 19050066;
        public static final int LabrynthArchfiend = 48745395;
        public static final int HarpiesPetDragonFearsomeFireBlast = 4991081;
        public static final int DynaHeroFurHire = 25123713;
        public static final int Hieracosphinx = 82260502;
        public static final int SpeedroidPassinglider = 26420373;
        public static final int TyrOfTheNordicChampions = 2333365;
        public static final int ValkyrianKnight = 99348756;
        public static final int Victoria = 75162696;
        public static final int MadolcheChouxvalier = 75363626;
        public static final int LadyOfD = 67511500;
        public static final int MermailAbysslung = 95466842;
        public static final int HarpiesPetBabyDragon = 6924874;
        public static final int HandHoldingGenie = 94535485;
        public static final int GolemDragon = 9666558;
        public static final int TwilightRoseKnight = 2986553;
        public static final int PerformapalThunderhino = 70458081;
        public static final int MiracleFlipper = 131182;
        public static final int Decoyroid = 25034083;
        public static final int AltergeistFifinellag = 12977245;
        public static final int BatterymanD = 55401221;
        public static final int Watthopper = 61380658;
        public static final int EgyptianGodSlime = 42166000;
        public static final int DinowrestlerChimeraTWrextle = 22900219;
        public static final int DinowrestlerGigaSpinosavate = 58672736;
        public static final int ScarredWarrior = 45298492;
        public static final int SharkFortress = 50449881;
        public static final int HeroicChampionClaivesolish = 97453744;
        public static final int GhostrickAlucard = 75367227;
        public static final int DinowrestlerKingTWrextle = 77967790;
        public static final int NumberF0UtopicFutureZexal = 41522092;

        public static final int PerformapalMissDirector = 92932860;
        public static final int AncientWarriorsMasterfulSunMou = 40140448;
        public static final int AncientWarriorsVirtuousLiuXuan = 40428851;
        public static final int CommandKnight = 10375182;
        public static final int HunterOwl = 51962254;
        public static final int RokketRecharger = 5969957;
        public static final int EmissaryOfTheOasis = 6103294;
        public static final int Zuttomozaurus = 24454387;
        public static final int Otoshidamashi = 14957440;
        public static final int NaturiaMosquito = 17285476;
        public static final int RescueACEHydrant = 37617348;
        public static final int MeizenTheBattleNinja = 11825276;
        public static final int VindikiteRGenex = 73483491;
        public static final int PrincessCologne = 75574498;
        public static final int Number48ShadowLich = 1426714;
        public static final int PhantomToken = 1426715;
        public static final int DuelLinkDragonTheDuelDragon = 60025883;
        public static final int DuelDragonToken = 60025884;
        public static final int SeleneQueenOfTheMasterMagicians = 45819647;
        public static final int TheWingedDragonofRaSphereMode = 10000080;
        public static final int SelettriceVaalmonica = 23093373;
        public static final int PerformageTrapezeWitch = 33206889;
        public static final int PoseidraTheStormingAtlantean = 99193444;

        public static final int RockOfTheVanquisher = 28168628;
        public static final int SpiralDischarge = 29477860;
        public static final int GaiaTheDragonChampion = 66889139;
        public static final int CrusadiaVanguard = 55312487;
        public static final int GladiatorBeastDomitianus = 33652635;
        public static final int PatricianOfDarkness = 19153634;
        public static final int DictatorOfD = 66961194;

        public static final int LoThePrayersOfTheVoicelessVoice = 25801745;
        public static final int BarrierOfTheVoicelessVoice = 98477480;

        public static final int DiabellzeOfTheOriginalSin = 53765052;
        public static final int PotOfExtravagance = 49238328;

        private _CardId() {
        }
    }

    protected static final class _Setcode {
        public static final int Watt = 0xe;
        public static final int Speedroid = 0x2016;
        public static final int EarthboundImmortal = 0x1021;
        public static final int Naturia = 0x2a;
        public static final int Nordic = 0x42;
        public static final int TimeLord = 0x4a;

        public static final int Harpie = 0x64;
        public static final int Madolche = 0x71;
        public static final int Ghostrick = 0x8d;
        public static final int OddEyes = 0x99;
        public static final int Performapal = 0x9f;
        public static final int Performage = 0xc6;
        public static final int BlueEyes = 0xdd;
        public static final int Amorphage = 0xe0;
        public static final int FurHire = 0x114;
        public static final int Altergeist = 0x103;
        public static final int Crusadia = 0x116;
        public static final int Danger = 0x11e;
        public static final int Endymion = 0x12a;
        public static final int AncientWarriors = 0x137;
        public static final int RescueACE = 0x18b;
        public static final int VanquishSoul = 0x195;
        public static final int Horus = 0x19d;

        private _Setcode() {
        }
    }

    /** {@code PlayerHintType.DescAdd} / {@code DescRemove}. */
    private static final int PLAYER_HINT_DESC_ADD = 6;
    private static final int PLAYER_HINT_DESC_REMOVE = 7;

    private final Map<Integer, Integer> calledbytheGraveIdCountMap = new HashMap<>();
    private final List<Integer> crossoutDesignatorIdList = new ArrayList<>();
    private int mistakenArrestAffectedCount = 0;
    /** Remaining turns of Dimension Shifter: 2 on resolve, decremented each new turn (until end of next turn). */
    private int dimensionShifterCount = 0;
    /**
     * Copies of Retaliating "C" still on the field after being Special Summoned by their own effect. The GY redirect
     * only applies while that summon flag remains.
     */
    private final List<ClientCard> retaliatingCSummonedByOwnEffect = new ArrayList<>();
    /** Effect IDs that have been resolved this turn. */
    protected final List<Integer> resolvedEffectIdList = new ArrayList<>();
    /** Effect IDs that have been resolved by the enemy this turn. */
    protected final List<Integer> enemyResolvedEffectIdList = new ArrayList<>();

    /** Columns 0-4 of the bot's field negated by Infinite Impermanence (enemy column c is 4-c). Cleared each turn. */
    protected final List<Integer> infiniteImpermanenceNegatedColumns = new ArrayList<>();

    /** While a monster with the KEY code is on the field, other monsters matching the VALUE can't be attacked. */
    protected final Map<Integer, Predicate<ClientCard>> defenderProtectRule = new HashMap<>();

    /** A monster with the KEY code can't be attacked while VALUE(monster, all enemy monsters) holds. */
    protected final Map<Integer, BiPredicate<ClientCard, List<ClientCard>>> defenderInvisbleRule = new HashMap<>();

    {
        Map<Integer, Predicate<ClientCard>> p = defenderProtectRule;
        p.put(_CardId.BelialMarquisOfDarkness, defender -> defender.isFaceup());
        p.put(_CardId.ChirubiméPrincessOfAutumnLeaves, defender -> defender.hasRace(CardRace.PLANT));
        p.put(_CardId.PerformapalBarokuriboh, defender -> true);
        p.put(_CardId.LabrynthArchfiend, defender -> defender.hasRace(CardRace.FIEND)
                && !defender.isCode(_CardId.LabrynthArchfiend));
        p.put(_CardId.HarpiesPetDragonFearsomeFireBlast, defender -> defender.level <= 6
                && defender.hasSetcode(_Setcode.Harpie));
        p.put(_CardId.DynaHeroFurHire, defender -> defender.hasSetcode(_Setcode.FurHire));
        p.put(_CardId.Hieracosphinx, defender -> defender.isFacedown());
        p.put(_CardId.SpeedroidPassinglider, defender -> defender.hasSetcode(_Setcode.Speedroid));
        p.put(_CardId.TyrOfTheNordicChampions, defender -> defender.hasSetcode(_Setcode.Nordic));
        p.put(_CardId.ValkyrianKnight, defender -> defender.hasRace(CardRace.WARRIOR)
                && !defender.isCode(_CardId.ValkyrianKnight));
        p.put(_CardId.Victoria, defender -> defender.hasRace(CardRace.FAIRY));
        p.put(_CardId.MadolcheChouxvalier, defender -> defender.hasSetcode(_Setcode.Madolche)
                && !defender.isCode(_CardId.MadolcheChouxvalier));
        p.put(_CardId.LadyOfD, defender -> defender.hasRace(CardRace.DRAGON));
        p.put(_CardId.MermailAbysslung, defender -> defender.hasAttribute(CardAttribute.WATER));
        p.put(_CardId.HarpiesPetBabyDragon, defender -> defender.hasSetcode(_Setcode.Harpie)
                && !defender.isCode(_CardId.HarpiesPetBabyDragon));
        p.put(_CardId.HandHoldingGenie, defender -> true);
        p.put(_CardId.GolemDragon, defender -> defender.hasRace(CardRace.DRAGON));
        p.put(_CardId.MaraudingCaptain, defender -> defender.hasRace(CardRace.WARRIOR));
        p.put(_CardId.TwilightRoseKnight, defender -> defender.hasRace(CardRace.PLANT));
        p.put(_CardId.PerformapalThunderhino, defender -> defender.hasSetcode(_Setcode.Performapal));
        p.put(_CardId.MiracleFlipper, defender -> defender.isFaceup());
        p.put(_CardId.Decoyroid, defender -> defender.isFaceup());
        p.put(_CardId.DupeFrog, defender -> true);
        p.put(_CardId.AltergeistFifinellag, defender -> defender.hasSetcode(_Setcode.Altergeist));
        p.put(_CardId.BatterymanD, defender -> defender.hasRace(CardRace.THUNDER)
                && !defender.isCode(_CardId.BatterymanD));
        p.put(_CardId.Watthopper, defender -> defender.hasSetcode(_Setcode.Watt) && defender.isFaceup());

        p.put(_CardId.EgyptianGodSlime, defender -> true);
        p.put(_CardId.DinowrestlerChimeraTWrextle, defender -> true);
        p.put(_CardId.DinowrestlerGigaSpinosavate, defender -> true);
        p.put(_CardId.ScarredWarrior, defender -> defender.hasRace(CardRace.WARRIOR) && defender.isFaceup());
        p.put(_CardId.SharkFortress, defender -> true);
        p.put(_CardId.HeroicChampionClaivesolish, defender -> true);
        p.put(_CardId.GhostrickAlucard, defender -> defender.hasSetcode(_Setcode.Ghostrick)
                || defender.isFacedown());
        p.put(_CardId.MekkKnightCrusadiaAstram, defender -> true);
        p.put(_CardId.DinowrestlerKingTWrextle, defender -> true);
        p.put(_CardId.NumberF0UtopicFutureZexal, defender -> true);

        Map<Integer, BiPredicate<ClientCard, List<ClientCard>>> v = defenderInvisbleRule;
        v.put(_CardId.UltimayaTzolkin, (defender, list) -> list.stream()
                .anyMatch(monster -> monster != defender && monster.hasType(CardType.SYNCHRO)));
        v.put(_CardId.PerformapalMissDirector, (defender, list) -> list.stream()
                .anyMatch(monster -> monster.hasSetcode(_Setcode.OddEyes)));
        v.put(_CardId.AncientWarriorsMasterfulSunMou, (defender, list) -> list.stream()
                .anyMatch(monster -> monster != defender && monster.hasSetcode(_Setcode.AncientWarriors)));
        v.put(_CardId.AncientWarriorsVirtuousLiuXuan, (defender, list) -> list.stream()
                .anyMatch(monster -> monster != defender && monster.hasSetcode(_Setcode.AncientWarriors)));
        v.put(_CardId.CommandKnight, (defender, list) -> list.stream()
                .anyMatch(monster -> monster != defender));
        v.put(_CardId.HunterOwl, (defender, list) -> list.stream()
                .anyMatch(monster -> monster != defender && monster.hasAttribute(CardAttribute.WIND)));
        v.put(_CardId.RokketRecharger, (defender, list) -> list.stream()
                .anyMatch(monster -> monster.isExtraCard() && monster.hasAttribute(CardAttribute.DARK)));
        v.put(_CardId.EmissaryOfTheOasis, (defender, list) -> list.stream()
                .anyMatch(monster -> monster.hasType(CardType.NORMAL) && monster.level <= 3));
        v.put(_CardId.Zuttomozaurus, (defender, list) -> list.stream()
                .anyMatch(monster -> monster != defender && monster.hasRace(CardRace.DINOSAUR)));
        v.put(_CardId.Otoshidamashi, (defender, list) -> list.stream()
                .anyMatch(monster -> !monster.hasType(CardType.TUNER)));
        v.put(_CardId.NaturiaMosquito, (defender, list) -> list.stream()
                .anyMatch(monster -> monster != defender && monster.hasSetcode(_Setcode.Naturia)));
        v.put(_CardId.RescueACEHydrant, (defender, list) -> list.stream()
                .anyMatch(monster -> !monster.isCode(_CardId.RescueACEHydrant)
                        && monster.hasSetcode(_Setcode.RescueACE)));

        v.put(_CardId.MeizenTheBattleNinja, (defender, list) -> list.stream()
                .anyMatch(monster -> monster.isFacedown()));
        v.put(_CardId.VindikiteRGenex, (defender, list) -> true);
        v.put(_CardId.PrincessCologne, (defender, list) -> list.stream()
                .anyMatch(monster -> monster != defender));
        v.put(_CardId.Number48ShadowLich, (defender, list) -> list.stream()
                .anyMatch(monster -> monster.isCode(_CardId.PhantomToken)));
        v.put(_CardId.DuelLinkDragonTheDuelDragon, (defender, list) -> list.stream()
                .anyMatch(monster -> monster.isCode(_CardId.DuelDragonToken)));
        v.put(_CardId.SeleneQueenOfTheMasterMagicians, (defender, list) -> list.stream()
                .anyMatch(monster -> monster.hasSetcode(_Setcode.Endymion)));

        v.put(_CardId.TheWingedDragonofRaSphereMode, (defender, list) -> true);
        v.put(_CardId.SelettriceVaalmonica, (defender, list) -> list.stream()
                .anyMatch(monster -> !monster.isCode(_CardId.SelettriceVaalmonica)));
        v.put(_CardId.PerformageTrapezeWitch, (defender, list) -> list.stream()
                .anyMatch(monster -> !monster.isCode(_CardId.PerformageTrapezeWitch)
                        && monster.hasSetcode(_Setcode.Performage)));
        v.put(_CardId.PoseidraTheStormingAtlantean, (defender, list) -> list.stream()
                .anyMatch(monster -> !monster.isCode(_CardId.PoseidraTheStormingAtlantean)));
    }

    protected DefaultExecutor(GameAI ai, Duel duel) {
        super(ai, duel);
        addExecutor(ExecutorType.ACTIVATE, _CardId.ChickenGame, this::defaultChickenGame);
        addExecutor(ExecutorType.ACTIVATE, _CardId.VaylantzWorld_ShinraBansho,
                this::defaultVaylantzWorld_ShinraBansho);
        addExecutor(ExecutorType.ACTIVATE, _CardId.VaylantzWorld_KonigWissen, this::defaultVaylantzWorld_KonigWissen);
        addExecutor(ExecutorType.ACTIVATE, _CardId.SantaClaws);
        addExecutor(ExecutorType.SPELL_SET, this::defaultSetForDiabellze);
    }

    /**
     * Decides which card the attacker should attack.
     *
     * @return the attack, or {@code null} to let the AI check the next attacker
     */
    @Override
    public BattlePhaseAction onSelectAttackTarget(ClientCard attacker, List<ClientCard> defenders) {
        for (ClientCard defender : defenders) {
            attacker.realPower = attacker.getAttackPower();
            defender.realPower = defender.getDefensePower();
            if (!onPreBattleBetween(attacker, defender)) {
                continue;
            }

            if (attacker.realPower > defender.realPower || (attacker.realPower >= defender.realPower
                    && attacker.isLastAttacker && defender.isAttack())) {
                return ai.attack(attacker, defender);
            }
        }

        if (attacker.canDirectAttack) {
            return ai.attack(attacker, null);
        }

        return null;
    }

    /**
     * Decides whether to declare an attack between attacker and defender. Can be overridden to update the
     * {@link ClientCard#realPower} of the attacker for cards like Honest.
     *
     * @return false if the attack shouldn't be done
     */
    @Override
    public boolean onPreBattleBetween(ClientCard attacker, ClientCard defender) {
        if (!attacker.isMonsterHasPreventActivationEffectInBattle()) {
            if (defender.isMonsterInvincible() && defender.isDefense()) {
                return false;
            }

            if (defender.isMonsterDangerous()) {
                boolean canIgnoreIt = !attacker.isDisabled() && (
                        attacker.isCode(_CardId.UltimateConductorTytanno) && defender.isDefense()
                        || attacker.isCode(_CardId.ElShaddollConstruct) && defender.isSpecialSummoned
                        || attacker.isCode(_CardId.AllyOfJusticeCatastor)
                                && !defender.hasAttribute(CardAttribute.DARK));
                if (!canIgnoreIt) {
                    return false;
                }
            }

            if (defender.equipCards.stream().anyMatch(equip ->
                    equip.isCode(_CardId.MoonMirrorShield, _CardId.MirrorBarrier) && !equip.isDisabled())) {
                return false;
            }

            if (!defender.isDisabled()) {
                if (defender.isCode(_CardId.MekkKnightCrusadiaAstram) && defender.isAttack()
                        && attacker.isSpecialSummoned) {
                    defender.realPower += attacker.attack;
                }

                if (defender.isCode(_CardId.CrystalWingSynchroDragon) && defender.isAttack() && attacker.level >= 5) {
                    defender.realPower += attacker.attack;
                }

                if (defender.isCode(_CardId.AllyOfJusticeCatastor) && !attacker.hasAttribute(CardAttribute.DARK)) {
                    return false;
                }

                if (defender.isCode(_CardId.NumberS39UtopiaTheLightning) && defender.isAttack()
                        && defender.hasXyzMaterial(2, _CardId.Number39Utopia)) {
                    defender.realPower = 5000;
                }

                if (defender.isCode(_CardId.VampireFraeulein)) {
                    defender.realPower += (enemy.lifePoints > 3000) ? 3000 : (enemy.lifePoints - 100);
                }

                if (defender.isCode(_CardId.InjectionFairyLily) && enemy.lifePoints > 2000) {
                    defender.realPower += 3000;
                }
            }
        }

        if (!defender.isMonsterHasPreventActivationEffectInBattle()) {
            if (attacker.isCode(_CardId.NumberS39UtopiaTheLightning) && !attacker.isDisabled()
                    && attacker.hasXyzMaterial(2, _CardId.Number39Utopia)) {
                attacker.realPower = 5000;
            }

            if (attacker.isCode(_CardId.EaterOfMillions) && !attacker.isDisabled()) {
                attacker.realPower = 9999;
            }

            if (attacker.isMonsterInvincible()) {
                attacker.realPower = 9999;
            }

            if (attacker.equipCards.stream().anyMatch(equip ->
                    equip.isCode(_CardId.MoonMirrorShield) && !equip.isDisabled())) {
                attacker.realPower = defender.realPower + 100;
            }
        }

        for (ClientCard protecter : enemy.getMonsters()) {
            if (protecter.isFaceup() && !protecter.isDisabled() && protecter != defender) {
                Predicate<ClientCard> defenderRule = defenderProtectRule.get(protecter.id);
                if (defenderRule != null && defenderRule.test(defender)) {
                    return false;
                }
            }
        }

        if (attacker.equipCards.stream().anyMatch(equip ->
                equip.isCode(_CardId.MoonMirrorShield) && !equip.isDisabled())) {
            attacker.realPower = defender.realPower + 100;
        }

        if (!defender.isDisabled()) {
            BiPredicate<ClientCard, List<ClientCard>> defenderRule = defenderInvisbleRule.get(defender.id);
            if (defenderRule != null && defenderRule.test(defender, enemy.getMonsters())) {
                return false;
            }
        }

        if (enemy.getMonsters().stream().anyMatch(monster -> monster != defender
                && monster.isCode(_CardId.HamonLordofStrikingThunder) && !monster.isDisabled()
                && monster.isDefense())) {
            return false;
        }

        if (defender.ownTargets.stream().anyMatch(c ->
                c.isCode(_CardId.PhantomKnightsFogBlade) && !c.isDisabled())) {
            return false;
        }

        if (defender.hasSetcode(_Setcode.EarthboundImmortal) && !defender.isDisabled()) {
            return false;
        }

        boolean attackHighestMonster =
                enemy.hasInMonstersZone(_CardId.RockOfTheVanquisher, true)
                        && enemy.getMonsters().stream().anyMatch(c -> c.hasSetcode(_Setcode.VanquishSoul))
                || enemy.hasInMonstersZone(_CardId.GladiatorBeastDomitianus, true)
                || enemy.hasInMonstersZone(_CardId.PatricianOfDarkness)
                || enemy.hasInMonstersZone(_CardId.DictatorOfD, true)
                        && enemy.getMonsters().stream().anyMatch(c -> c.hasSetcode(_Setcode.BlueEyes));
        if (attackHighestMonster) {
            if (defender.hasPosition(CardPosition.FACE_DOWN)) {
                return false;
            }
            if (enemy.getMonsters().stream().anyMatch(c -> c.isFaceup() && c.attack > defender.attack)) {
                return false;
            }
        }

        if (enemy.hasInSpellZone(_CardId.SpiralDischarge, true)
                && enemy.hasInMonstersZone(_CardId.GaiaTheDragonChampion)
                && !defender.isCode(_CardId.GaiaTheDragonChampion)) {
            return false;
        }

        if (enemy.hasInSpellZone(_CardId.CrusadiaVanguard, true)
                && enemy.getMonsters().stream().anyMatch(c -> c.hasSetcode(_Setcode.Crusadia)
                        && c.hasType(CardType.LINK))
                && !defender.hasType(CardType.LINK)) {
            return false;
        }

        if (defender.isCode(_CardId.RescueACEHydrant) && !defender.isDisabled()
                && enemy.getMonsters().stream().anyMatch(monster -> monster.hasSetcode(_Setcode.RescueACE)
                        && !monster.isCode(_CardId.RescueACEHydrant))) {
            return false;
        }

        if (enemy.hasInSpellZone(_CardId.BarrierOfTheVoicelessVoice, true)
                && enemy.hasInMonstersZone(_CardId.LoThePrayersOfTheVoicelessVoice, false, false, true)
                && enemy.getMonsters().stream().anyMatch(c -> c.hasType(CardType.RITUAL) && c.isFaceup())
                && !defender.hasType(CardType.RITUAL)) {
            return false;
        }

        return true;
    }

    @Override
    public boolean onPreActivate(ClientCard card) {
        ClientCard lastChainCard = util.getLastChainCard();
        if (lastChainCard != null && duel.phase == DuelPhase.STANDBY
                && lastChainCard.isCode(
                        _CardId.SandaionTheTimelord,
                        _CardId.GabrionTheTimelord,
                        _CardId.MichionTheTimelord,
                        _CardId.ZaphionTheTimelord,
                        _CardId.HailonTheTimelord,
                        _CardId.RaphionTheTimelord,
                        _CardId.SadionTheTimelord,
                        _CardId.MetaionTheTimelord,
                        _CardId.KamionTheTimelord,
                        _CardId.LazionTheTimelord)) {
            return false;
        }
        if ((card.location == CardLocation.HAND || card.location == CardLocation.SPELL_ZONE && card.isFacedown())
                && (card.isSpell() && defaultSpellWillBeNegated(card)
                        || card.isTrap() && defaultTrapWillBeNegated(card))) {
            return false;
        }
        return true;
    }

    /**
     * Called when the AI has to select a card position.
     *
     * @return the selected position, or 0 if no position is set for this card
     */
    @Override
    public int onSelectPosition(int cardId, List<Integer> positions) {
        if (ai.haveSelectedPosition()) {
            return 0;
        }

        CardInfo cardData = NamedCards.get(cardId);
        if (cardData != null) {
            if (cardData.data().attack() == 0) {
                return CardPosition.FACE_UP_DEFENCE;
            }
        }
        return 0;
    }

    @Override
    public boolean onSelectBattleReplay() {
        if (bot.battlingMonster == null) {
            return false;
        }
        List<ClientCard> defenders = duel.fields[1].getMonsters();
        if (defenders.isEmpty()) {
            return true;
        }
        defenders.sort(CardContainer::compareDefensePower);
        Collections.reverse(defenders);
        BattlePhaseAction result = onSelectAttackTarget(bot.battlingMonster, defenders);
        if (result != null && result.action == BattlePhaseAction.BattleAction.ATTACK) {
            return true;
        }
        return false;
    }

    /** Set when this card can't beat the enemies. */
    @Override
    public boolean onSelectMonsterSummonOrSet(ClientCard card) {
        return card.level <= 4 && bot.getMonsters().stream().noneMatch(ClientCard::isFaceup)
                && util.isAllEnemyBetterThanValue(card.attack, true);
    }

    /**
     * Called when the AI has to select one or more cards.
     *
     * @return the selected cards, or {@code null} to leave it to the AI
     */
    @Override
    public List<ClientCard> onSelectCard(List<ClientCard> cards, int min, int max, long hint, boolean cancelable) {
        ChainInfo currentSolving = duel.getCurrentSolvingChainInfo();
        if (currentSolving != null
                && currentSolving.isActivateCode(_CardId.DogmatikaAlbaZoa)
                && currentSolving.activatePlayer == 1) {
            List<ClientCard> extraDeck = new ArrayList<>(bot.extraDeck);
            int shuffleCount = extraDeck.size();
            while (shuffleCount-- > 1) {
                int index = random.nextInt(extraDeck.size());
                ClientCard tempCard = extraDeck.get(shuffleCount);
                extraDeck.set(shuffleCount, extraDeck.get(index));
                extraDeck.set(index, tempCard);
            }

            return util.checkSelectCount(extraDeck, cards, min, max);
        }

        return null;
    }

    @Override
    public void onPlayerHint(int player, int hintType, long description) {
        super.onPlayerHint(player, hintType, description);
        if (player != 0 && player != 1) {
            return;
        }
        ClientField field = (player == 0) ? bot : enemy;
        if (hintType == PLAYER_HINT_DESC_ADD) {
            field.hintDescriptions.add(description);
        } else if (hintType == PLAYER_HINT_DESC_REMOVE) {
            field.hintDescriptions.remove(description);
        }
    }

    @Override
    public void onHintZone(int player, long zone) {
        super.onHintZone(player, zone);
        ChainInfo currentChainInfo = duel.getCurrentSolvingChainInfo();
        if (currentChainInfo != null) {
            if (currentChainInfo.isActivateCode(_CardId.InfiniteImpermanence)) {
                // Zone bits: 0x100 = column 0, 0x200 = column 1, ..., 0x1000 = column 4.
                for (int i = 0; i <= 4; i++) {
                    if ((zone & (0x100L << i)) == 0) {
                        continue;
                    }
                    if (currentChainInfo.activatePlayer == 0) {
                        infiniteImpermanenceNegatedColumns.add(i);
                    } else {
                        infiniteImpermanenceNegatedColumns.add(4 - i);
                    }
                }
            }
        }
    }

    @Override
    public void onChainSolved(int chainIndex) {
        ChainInfo currentChain = duel.getCurrentSolvingChainInfo();
        if (currentChain != null && !duel.isCurrentSolvingChainNegated()) {
            if (currentChain.isActivateCode(_CardId.LockBird)) {
                resolvedEffectIdList.add(_CardId.LockBird);
            }
            // Different Dimension Ground only lasts until the end of the turn it resolves.
            if (currentChain.isActivateCode(_CardId.DifferentDimensionGround)) {
                resolvedEffectIdList.add(_CardId.DifferentDimensionGround);
            }
            // Artifact Lancea's quick effect (str2) prevents banishing until end of turn; its GY trigger (str1) does
            // not.
            if (currentChain.isActivateCode(_CardId.ArtifactLancea)
                    && currentChain.activateDescription == util.getStringId(_CardId.ArtifactLancea, 1)) {
                resolvedEffectIdList.add(_CardId.ArtifactLancea);
            }
            // Dimension Shifter banishes instead of sending to the GY for both players until the end of next turn.
            if (currentChain.isActivateCode(_CardId.DimensionShifter)) {
                dimensionShifterCount = 2;
            }
            if (currentChain.activatePlayer == 1) {
                if (currentChain.isActivateCode(_CardId.MaxxC)) {
                    enemyResolvedEffectIdList.add(_CardId.MaxxC);
                }
                if (currentChain.isActivateCode(_CardId.MulcharmyPurulia)) {
                    enemyResolvedEffectIdList.add(_CardId.MulcharmyPurulia);
                }
                if (currentChain.isActivateCode(_CardId.MulcharmyFuwalos)) {
                    enemyResolvedEffectIdList.add(_CardId.MulcharmyFuwalos);
                }
                if (currentChain.isActivateCode(_CardId.MulcharmyNyalus)) {
                    enemyResolvedEffectIdList.add(_CardId.MulcharmyNyalus);
                }
                if (currentChain.isActivateCode(_CardId.MistakenArrest)) {
                    if (duel.player == 1) {
                        mistakenArrestAffectedCount = Math.max(mistakenArrestAffectedCount, 3);
                    } else {
                        mistakenArrestAffectedCount = Math.max(mistakenArrestAffectedCount, 2);
                    }
                }
            }
        }
        super.onChainSolved(chainIndex);
    }

    /** Resets the per-turn state. */
    @Override
    public void onNewTurn() {
        infiniteImpermanenceNegatedColumns.clear();
        resolvedEffectIdList.clear();
        enemyResolvedEffectIdList.clear();
        if (duel.turn <= 1) {
            calledbytheGraveIdCountMap.clear();
            mistakenArrestAffectedCount = 0;
            dimensionShifterCount = 0;
            retaliatingCSummonedByOwnEffect.clear();
        }
        mistakenArrestAffectedCount = Math.max(mistakenArrestAffectedCount - 1, 0);
        if (dimensionShifterCount > 0) {
            dimensionShifterCount--;
        }
        for (Map.Entry<Integer, Integer> entry : calledbytheGraveIdCountMap.entrySet()) {
            if (entry.getValue() > 0) {
                entry.setValue(entry.getValue() - 1);
            }
        }
        crossoutDesignatorIdList.clear();

        super.onNewTurn();
    }

    @Override
    public void onMove(ClientCard card, int previousControler, int previousLocation, int currentControler,
                       int currentLocation) {
        if (card != null) {
            // Retaliating "C" loses its own-effect flag when it leaves the Monster Zone or is set face-down.
            if ((currentLocation & CardLocation.MONSTER_ZONE) == 0 || card.isFacedown()) {
                retaliatingCSummonedByOwnEffect.remove(card);
            }
            // Identify the card name by the activation snapshot, since the related card's id may change after it
            // leaves the field.
            ChainInfo currentSolvingChain = duel.getCurrentSolvingChainInfo();
            if (currentSolvingChain != null && currentLocation == CardLocation.REMOVED) {
                int originId = card.id;
                if (card.data != null) {
                    if (card.data.data().alias() > 0) {
                        originId = card.data.data().alias();
                    } else {
                        originId = card.id;
                    }
                }
                if (currentSolvingChain.isActivateCode(_CardId.CalledByTheGrave)) {
                    calledbytheGraveIdCountMap.put(originId, 2);
                }
                if (currentSolvingChain.isActivateCode(_CardId.CrossoutDesignator)) {
                    crossoutDesignatorIdList.add(originId);
                }
            }
        }
        super.onMove(card, previousControler, previousLocation, currentControler, currentLocation);
    }

    /** Drops Retaliating "C" tracking when it is set face-down; the script flag resets on SET. */
    @Override
    public void onPosChange(ClientCard card, int previousPosition, int currentPosition) {
        if (card != null && (currentPosition & CardPosition.FACE_DOWN) != 0) {
            retaliatingCSummonedByOwnEffect.remove(card);
        }
        super.onPosChange(card, previousPosition, currentPosition);
    }

    /** Tracks Retaliating "C" Special Summoned by its own effect so its GY redirect can be recognized later. */
    @Override
    public void onSpSummoned() {
        ChainInfo currentChain = duel.getCurrentSolvingChainInfo();
        // The GY redirect of Retaliating "C" only applies when it Special Summons itself by its own effect.
        if (currentChain != null && !duel.isCurrentSolvingChainNegated()
                && currentChain.isActivateCode(_CardId.RetaliatingC)) {
            for (ClientCard summoned : duel.lastSummonedCards) {
                if (summoned != null && summoned.isCode(_CardId.RetaliatingC)
                        && !retaliatingCSummonedByOwnEffect.contains(summoned)) {
                    retaliatingCSummonedByOwnEffect.add(summoned);
                }
            }
        }
        super.onSpSummoned();
    }

    /** Destroy face-down cards first, in our turn. */
    protected boolean defaultMysticalSpaceTyphoon() {
        if (duel.currentChain.stream().anyMatch(c -> c.isCode(_CardId.MysticalSpaceTyphoon))) {
            return false;
        }

        List<ClientCard> spells = enemy.getSpells();
        if (spells.isEmpty()) {
            return false;
        }

        ClientCard selected = CardContainer.getFloodgate(Arrays.asList(enemy.spellZone));

        if (selected == null) {
            if (duel.player == 0) {
                selected = spells.stream().filter(ClientCard::isFacedown).findFirst().orElse(null);
            }
            if (duel.player == 1) {
                selected = spells.stream().filter(c -> c.hasType(CardType.CONTINUOUS) || c.hasType(CardType.EQUIP)
                        || c.hasType(CardType.FIELD)).findFirst().orElse(null);
            }
        }

        if (selected == null) {
            return false;
        }
        ai.selectCard(selected);
        return true;
    }

    /** Destroy face-down cards first, in our turn. */
    protected boolean defaultCosmicCyclone() {
        for (ClientCard c : duel.currentChain) {
            if (c.isCode(_CardId.CosmicCyclone)) {
                return false;
            }
        }
        return (bot.lifePoints > 1000) && defaultMysticalSpaceTyphoon();
    }

    /** Activate if available. */
    protected boolean defaultGalaxyCyclone() {
        List<ClientCard> spells = enemy.getSpells();
        if (spells.isEmpty()) {
            return false;
        }

        ClientCard selected;

        if (card.location == CardLocation.GRAVE) {
            selected = util.getBestEnemySpell(true);
        } else {
            selected = spells.stream().filter(ClientCard::isFacedown).findFirst().orElse(null);
        }

        if (selected == null) {
            return false;
        }

        ai.selectCard(selected);
        return true;
    }

    /** Set the highest ATK level 4+ effect enemy monster. */
    protected boolean defaultBookOfMoon() {
        if (util.isAllEnemyBetter(true)) {
            List<ClientCard> candidates = enemy.getMonsters().stream()
                    .filter(c -> !isCardAlreadyHandledInCurrentChain(c)).toList();
            ClientCard monster = CardContainer.getHighestAttackMonster(candidates, true);
            if (monster != null && monster.hasType(CardType.EFFECT) && !monster.hasType(CardType.LINK)
                    && (monster.hasType(CardType.XYZ) || monster.level > 4)) {
                ai.selectCard(monster);
                return true;
            }
        }
        return false;
    }

    /** Return a problematic monster, and if this card becomes a target, return any enemy monster. */
    protected boolean defaultCompulsoryEvacuationDevice() {
        ClientCard target = util.getProblematicEnemyMonster(0, true);
        if (target != null && !isCardAlreadyHandledInCurrentChain(target)) {
            ai.selectCard(target);
            return true;
        }
        if (util.isChainTarget(card)) {
            ClientCard monster = util.getBestEnemyMonster(false, true);
            if (monster != null && !isCardAlreadyHandledInCurrentChain(monster)) {
                ai.selectCard(monster);
                return true;
            }
        }
        return false;
    }

    /** Revive the best monster when we don't have a better one on the field. */
    protected boolean defaultCallOfTheHaunted() {
        if (!util.isAllEnemyBetter(true)) {
            return false;
        }
        ClientCard selected = CardContainer.getMatchingCards(bot.graveyard, ClientCard::isCanRevive).stream()
                .sorted(Comparator.comparingInt((ClientCard c) -> c.attack).reversed())
                .findFirst().orElse(null);
        ai.selectCard(selected);
        return true;
    }

    /** Default Scapegoat effect. */
    protected boolean defaultScapegoat() {
        if (defaultSpellWillBeNegated()) {
            return false;
        }
        if (duel.player == 0) {
            return false;
        }
        if (duel.phase == DuelPhase.END) {
            return true;
        }
        if (defaultOnBecomeTarget()) {
            return true;
        }
        if (duel.phase > DuelPhase.MAIN1 && duel.phase < DuelPhase.MAIN2) {
            if (enemy.hasInMonstersZone(List.of(
                    _CardId.UltimateConductorTytanno,
                    _CardId.InvokedPurgatrio,
                    _CardId.ChaosAncientGearGiant,
                    _CardId.UltimateAncientGearGolem,
                    _CardId.RedDragonArchfiend), true, false, false)) {
                return false;
            }
            if (util.getTotalAttackingMonsterAttack(1) >= bot.lifePoints) {
                return true;
            }
        }
        return false;
    }

    /** Always activate in the opponent's turn. */
    protected boolean defaultMaxxC() {
        if (defaultCheckWhetherCardIsNegated(card)) {
            return false;
        }
        return duel.player == 1;
    }

    /** Always negate the opponent's effect, except for some cards like Upstart Goblin. */
    protected boolean defaultAshBlossomAndJoyousSpring() {
        if (defaultCheckWhetherCardIsNegated(card)) {
            return false;
        }
        int[] ignoreList = {
            _CardId.MacroCosmos,
            _CardId.UpstartGoblin,
            _CardId.CyberEmergency,
            _CardId.TheAgentOfCreationVenus
        };
        if (util.getLastChainCard().isCode(ignoreList)) {
            return false;
        }
        // "Danger!" hand effects
        if (util.getLastChainCard().hasSetcode(_Setcode.Danger)
                && util.getLastChainCard().location == CardLocation.HAND) {
            return false;
        }
        return duel.lastChainPlayer == 1;
    }

    /** Always activate unless the activating card is disabled. */
    protected boolean defaultGhostOgreAndSnowRabbit() {
        if (defaultCheckWhetherCardIsNegated(card)) {
            return false;
        }
        if (util.getLastChainCard() != null && util.getLastChainCard().isDisabled()) {
            return false;
        }
        return defaultTrap();
    }

    /** Always negate the opponent's effect. */
    protected boolean defaultGhostBelleAndHauntedMansion() {
        if (defaultCheckWhetherCardIsNegated(card)) {
            return false;
        }
        return defaultTrap();
    }

    /** Same as {@link #defaultBreakthroughSkill()}. */
    protected boolean defaultEffectVeiler() {
        if (defaultCheckWhetherCardIsNegated(card)) {
            return false;
        }
        ClientCard lastChainCard = util.getLastChainCard();
        if (lastChainCard != null && (lastChainCard.isCode(_CardId.GalaxySoldier) && enemy.hand.size() >= 3
                || lastChainCard.isCode(_CardId.EffectVeiler, _CardId.InfiniteImpermanence))) {
            return false;
        }
        return defaultBreakthroughSkill();
    }

    /** Chain common hand traps. */
    protected boolean defaultCalledByTheGrave() {
        int[] targetList = {
            _CardId.MaxxC,
            _CardId.LockBird,
            _CardId.GhostOgreAndSnowRabbit,
            _CardId.AshBlossom,
            _CardId.GhostBelle,
            _CardId.EffectVeiler,
            _CardId.ArtifactLancea
        };
        if (duel.lastChainPlayer == 1) {
            for (int id : targetList) {
                if (util.getLastChainCard().isCode(id)) {
                    ai.selectCard(id);
                    return uniqueFaceupSpell();
                }
            }
        }
        return false;
    }

    /** Default Infinite Impermanence effect. */
    protected boolean defaultInfiniteImpermanence() {
        if (defaultCheckWhetherCardIsNegated(card)) {
            return false;
        }
        // TODO: negate spells and traps
        ClientCard lastChainCard = util.getLastChainCard();
        if (lastChainCard != null && (lastChainCard.isCode(_CardId.GalaxySoldier) && enemy.hand.size() >= 3
                || lastChainCard.isCode(_CardId.EffectVeiler, _CardId.InfiniteImpermanence))) {
            return false;
        }
        return defaultDisableMonster();
    }

    /** Chain the enemy monster, or negate a monster like Rescue Rabbit. */
    protected boolean defaultBreakthroughSkill() {
        if (!defaultUniqueTrap()) {
            return false;
        }
        return defaultDisableMonster();
    }

    /** Chain the enemy monster, or negate a monster like Rescue Rabbit. */
    protected boolean defaultDisableMonster() {
        ClientCard target = defaultGetDisableMonsterTarget();
        if (target == null) {
            return false;
        }

        ai.selectCard(target);
        return true;
    }

    /** The enemy monster that the default logic should negate. */
    protected ClientCard defaultGetDisableMonsterTarget() {
        if (duel.player == 1) {
            ClientCard target = CardContainer.getShouldBeDisabledBeforeItUseEffectMonster(
                    Arrays.asList(enemy.monsterZone));
            if (target != null) {
                return target;
            }
        }

        ClientCard lastChainCard = util.getLastChainCard();

        if (lastChainCard != null && lastChainCard.controller == 1
                && lastChainCard.location == CardLocation.MONSTER_ZONE
                && !lastChainCard.isDisabled() && !lastChainCard.isShouldNotBeTarget()
                && !lastChainCard.isShouldNotBeSpellTrapTarget()) {
            return lastChainCard;
        }

        if (bot.battlingMonster != null && enemy.battlingMonster != null) {
            if (!enemy.battlingMonster.isDisabled() && enemy.battlingMonster.isCode(_CardId.EaterOfMillions)) {
                return enemy.battlingMonster;
            }
        }

        if (duel.phase == DuelPhase.BATTLE_START && duel.player == 1) {
            ClientCard target = CardContainer.getFirstMatchingCard(Arrays.asList(enemy.monsterZone), c ->
                    c.isCode(_CardId.NumberS39UtopiaTheLightning) && !c.isDisabled());
            if (target != null) {
                return target;
            }
        }

        return null;
    }

    /** Activate unless this card is the only target or we are summoning monsters. */
    protected boolean defaultSolemnJudgment() {
        ChainInfo lastChain = duel.currentChainInfo.isEmpty()
                ? null : duel.currentChainInfo.get(duel.currentChainInfo.size() - 1);
        if (lastChain != null
                && lastChain.activatePlayer == 1
                && lastChain.relatedCard.isCode(_CardId.MysticalSpaceTyphoon, _CardId.GalaxyCyclone)
                && lastChain.targets.stream().anyMatch(c -> c.isFacedown() && c.controller == 0)) {
            return false;
        }

        return !util.isChainTargetOnly(card) && !(duel.player == 0 && duel.lastChainPlayer == -1)
                && !defaultOnlyHorusSpSummoning() && defaultTrap();
    }

    /** Activate unless we are summoning monsters. */
    protected boolean defaultSolemnWarning() {
        ClientCard lastChainCard = util.getLastChainCard();
        return (bot.lifePoints > 2000) && !(duel.player == 0 && duel.lastChainPlayer == -1)
                && !defaultOnlyHorusSpSummoning()
                && !(lastChainCard != null && lastChainCard.isCode(_CardId.TreebornFrog))
                && defaultTrap();
    }

    /** Activate unless we are summoning monsters. */
    protected boolean defaultSolemnStrike() {
        return (bot.lifePoints > 1500) && !(duel.player == 0 && duel.lastChainPlayer == -1)
                && !defaultOnlyHorusSpSummoning() && defaultTrap();
    }

    /**
     * Whether only Horus monsters are being Special Summoned. If so, the summon shouldn't be negated since they can be
     * Special Summoned again.
     */
    protected boolean defaultOnlyHorusSpSummoning() {
        if (!duel.summoningCards.isEmpty()) {
            boolean notOnlyHorusFlag = false;
            for (ClientCard summoning : duel.summoningCards) {
                if (!summoning.hasSetcode(_Setcode.Horus) || summoning.lastLocation != CardLocation.GRAVE) {
                    notOnlyHorusFlag = true;
                    break;
                }
            }
            return !notOnlyHorusFlag;
        }
        return false;
    }

    /** Activate when all enemy monsters have better ATK. */
    protected boolean defaultTorrentialTribute() {
        if (util.hasChainedTrap(0) || !util.isAllEnemyBetter(true)) {
            return false;
        }

        return enemy.getMonsters().stream().anyMatch(c -> !isCardAlreadyHandledInCurrentChain(c));
    }

    /** Activate when the enemy has more Spells and Traps. */
    protected boolean defaultHeavyStorm() {
        return bot.getSpellCount() < enemy.getSpellCount();
    }

    /** Activate before other wipes if the enemy has 2 or more Spells and Traps. */
    protected boolean defaultHarpiesFeatherDusterFirst() {
        return enemy.getSpellCount() >= 2;
    }

    /** Activate when one enemy monster has better ATK. */
    protected boolean defaultHammerShot() {
        return util.isOneEnemyBetter(true);
    }

    /** Activate when one enemy monster has better ATK or DEF. */
    protected boolean defaultDarkHole() {
        return util.isOneEnemyBetter();
    }

    /** Activate when one enemy monster has better ATK or DEF. */
    protected boolean defaultRaigeki() {
        return util.isOneEnemyBetter();
    }

    /** Activate when one enemy monster has better ATK or DEF. */
    protected boolean defaultSmashingGround() {
        return util.isOneEnemyBetter();
    }

    /** Activate when we have more than 15 cards in the Deck. */
    protected boolean defaultPotOfDesires() {
        return bot.deck.size() > 15;
    }

    /** Set traps only, and avoid blocking the activation of other cards. */
    protected boolean defaultSpellSet() {
        return (card.isTrap() || card.hasType(CardType.QUICK_PLAY) || defaultSpellMustSetFirst())
                && bot.getSpellCountWithoutField() < 4;
    }

    /** Summon with no tribute, or with tributes of lower ATK. */
    protected boolean defaultMonsterSummon() {
        if (card.level <= 4) {
            return true;
        }

        if (!uniqueFaceupMonster()) {
            return false;
        }
        int tributecount = (int) Math.ceil((card.level - 4.0d) / 2.0d);
        for (int j = 0; j < 7; ++j) {
            ClientCard tributeCard = bot.monsterZone[j];
            if (tributeCard == null) {
                continue;
            }
            if (tributeCard.getDefensePower() < card.attack) {
                tributecount--;
            }
        }
        return tributecount <= 0;
    }

    /** Activate when we have no Field Spell. */
    protected boolean defaultField() {
        return bot.spellZone[5] == null;
    }

    /** Change position if all enemies are better. */
    protected boolean defaultMonsterRepos() {
        if (card.isMonsterInvincible()) {
            return card.isDefense();
        }

        if (card.attack == 0) {
            if (card.isFaceup() && card.isAttack()) {
                return true;
            }
            if (card.isFaceup() && card.isDefense()) {
                return false;
            }
        }

        if (enemy.hasInMonstersZone(_CardId.BlueEyesChaosMAXDragon, true)
                && card.isAttack() && (4000 - card.defense) * 2 > (4000 - card.attack)) {
            return false;
        }
        if (enemy.hasInMonstersZone(_CardId.BlueEyesChaosMAXDragon, true)
                && card.isDefense() && card.isFaceup()
                && (4000 - card.defense) * 2 > (4000 - card.attack)) {
            return true;
        }

        boolean enemyBetter = util.isAllEnemyBetter();
        if (card.isAttack() && enemyBetter) {
            return true;
        }
        if (card.isDefense() && !enemyBetter && (card.attack > card.defense
                || (duel.phase == DuelPhase.MAIN1 && card.attack >= util.getBestPower(enemy)))) {
            return true;
        }

        return false;
    }

    /** Whether the current card, as a spell, will be negated. */
    protected boolean defaultSpellWillBeNegated() {
        return defaultSpellWillBeNegated(null);
    }

    /** Whether a spell will be negated; {@code null} means the current card. */
    protected boolean defaultSpellWillBeNegated(ClientCard currentCard) {
        return (bot.hasInSpellZone(_CardId.ImperialOrder, true, true)
                || enemy.hasInSpellZone(_CardId.ImperialOrder, true))
                && !util.chainContainsCard(_CardId.ImperialOrder)
                || defaultCheckWhetherCardIsNegated(currentCard != null ? currentCard : card);
    }

    /** Whether the current card, as a trap, will be negated. */
    protected boolean defaultTrapWillBeNegated() {
        return defaultTrapWillBeNegated(null);
    }

    /** Whether a trap will be negated; {@code null} means the current card. */
    protected boolean defaultTrapWillBeNegated(ClientCard currentCard) {
        return (bot.hasInSpellZone(_CardId.RoyalDecreel, true, true)
                || enemy.hasInSpellZone(_CardId.RoyalDecreel, true))
                && !util.chainContainsCard(_CardId.RoyalDecreel)
                || defaultCheckWhetherCardIsNegated(currentCard != null ? currentCard : card);
    }

    /** Whether spells must be Set before they can be activated. */
    protected boolean defaultSpellMustSetFirst() {
        return bot.hasInSpellZone(_CardId.AntiSpellFragrance, true, true)
                || enemy.hasInSpellZone(_CardId.AntiSpellFragrance, true);
    }

    /** Whether this card is targeted, or the enemy activates a card like Harpie's Feather Duster. */
    protected boolean defaultOnBecomeTarget() {
        return defaultOnBecomeTarget(card);
    }

    protected boolean defaultOnBecomeTarget(ClientCard card) {
        if (card == null) {
            return false;
        }
        if (util.isChainTarget(card)) {
            return true;
        }
        int[] destroyAllList = {
            _CardId.EvilswarmExcitonKnight,
            _CardId.BlackRoseDragon,
            _CardId.JudgmentDragon,
            _CardId.TopologicTrisbaena,
            _CardId.EvenlyMatched,
            _CardId.DivineArsenalAAZEUS_SkyThunder
        };
        int[] destroyAllMonsterList = {
            _CardId.DarkHole,
            _CardId.InterruptedKaijuSlumber
        };
        int[] destroyAllOpponentMonsterList = {
            _CardId.Raigeki
        };
        List<Integer> destroyAllOpponentSpellList = List.of(
                _CardId.HarpiesFeatherDuster,
                _CardId.DarkMagicAttack);

        if (util.chainContainsCard(destroyAllList)) {
            return true;
        }
        if (enemy.hasInSpellZone(destroyAllOpponentSpellList, true, false)
                && card.location == CardLocation.SPELL_ZONE) {
            return true;
        }
        if (util.chainContainsCard(destroyAllMonsterList) && card.location == CardLocation.MONSTER_ZONE) {
            return true;
        }
        if (duel.currentChain.stream().anyMatch(c -> c.controller == 1 && c.isCode(destroyAllOpponentMonsterList))
                && card.location == CardLocation.MONSTER_ZONE) {
            return true;
        }
        if (duel.currentChainInfo.stream().anyMatch(chain ->
                chain.activatePlayer == 1
                        && chain.isActivateCode(_CardId.LightningStorm)
                        && chain.hasAnnounce(util.getStringId(_CardId.LightningStorm, 0)))
                && card.location == CardLocation.MONSTER_ZONE && card.isAttack()) {
            return true;
        }
        if (duel.currentChainInfo.stream().anyMatch(chain ->
                chain.activatePlayer == 1
                        && chain.isActivateCode(_CardId.LightningStorm)
                        && chain.hasAnnounce(util.getStringId(_CardId.LightningStorm, 1)))
                && card.location == CardLocation.SPELL_ZONE) {
            return true;
        }
        // TODO: chainContainsCard(id, player)
        return false;
    }

    /** Chain the enemy's activation or summon. */
    protected boolean defaultTrap() {
        if (defaultCheckWhetherCardIsNegated(card)) {
            return false;
        }
        return (duel.lastChainPlayer == -1 && duel.lastSummonPlayer != 0) || duel.lastChainPlayer == 1;
    }

    /** Activate when available and no other trap of ours is in this chain or face-up with the same name. */
    protected boolean defaultUniqueTrap() {
        if (util.hasChainedTrap(0)) {
            return false;
        }

        return uniqueFaceupSpell();
    }

    /** Activate Bottomless Trap Hole only if a summoned monster still needs handling. */
    protected boolean defaultBottomlessTrapHole() {
        if (!defaultUniqueTrap()) {
            return false;
        }

        return duel.lastSummonedCards.stream().anyMatch(c ->
                c.controller == 1
                        && c.attack >= 1500
                        && !isCardAlreadyHandledInCurrentChain(c));
    }

    /** Whether one of our earlier chain links will already neutralize this card. */
    protected boolean isCardAlreadyHandledInCurrentChain(ClientCard card) {
        if (card == null) {
            return false;
        }

        boolean targetedByOwnInterruption = duel.currentChainInfo.stream().anyMatch(chain ->
                chain.activatePlayer == 0
                        && chain.targets.contains(card)
                        && chain.isCode(
                                _CardId.BookOfMoon,
                                _CardId.CompulsoryEvacuationDevice,
                                _CardId.EnemyController,
                                _CardId.DimensionalPrison,
                                _CardId.RingOfDestruction,
                                _CardId.RaigekiBreak,
                                _CardId.KarmaCut,
                                _CardId.SakuretsuArmor,
                                _CardId.Crackdown,
                                _CardId.DogmatikaPunishment,
                                _CardId.PaleozoicDinomischus,
                                _CardId.DracobackTheDragonSteed,
                                _CardId.PhoenixWingWindBlast));
        if (targetedByOwnInterruption) {
            return true;
        }

        boolean ownAttackPositionBoardInterruption =
                card.controller == 1
                        && card.location == CardLocation.MONSTER_ZONE
                        && card.isAttack()
                        && duel.currentChainInfo.stream().anyMatch(chain ->
                                chain.activatePlayer == 0
                                        && chain.isCode(
                                                _CardId.DrowningMirrorForce,
                                                _CardId.BlazingMirrorForce,
                                                _CardId.StormingMirrorForce,
                                                _CardId.MirrorForce));
        if (ownAttackPositionBoardInterruption) {
            return true;
        }

        boolean ownPositionSpecificMirrorForce =
                card.controller == 1
                        && card.location == CardLocation.MONSTER_ZONE
                        && duel.currentChainInfo.stream().anyMatch(chain ->
                                chain.activatePlayer == 0
                                        && (chain.isCode(_CardId.QuakingMirrorForce)
                                                && card.isAttack()
                                                && !card.hasType(CardType.LINK)
                                        || chain.isCode(_CardId.DarkMirrorForce)
                                                && card.isDefense()));
        if (ownPositionSpecificMirrorForce) {
            return true;
        }

        boolean ownTorrentialTribute =
                card.controller == 1
                        && card.location == CardLocation.MONSTER_ZONE
                        && duel.currentChainInfo.stream().anyMatch(chain ->
                                chain.activatePlayer == 0
                                        && chain.isCode(_CardId.TorrentialTribute));
        if (ownTorrentialTribute) {
            return true;
        }

        boolean ownBottomlessTrapHole =
                card.controller == 1
                        && duel.lastSummonedCards.contains(card)
                        && card.attack >= 1500
                        && duel.currentChainInfo.stream().anyMatch(chain ->
                                chain.activatePlayer == 0
                                        && chain.isCode(_CardId.BottomlessTrapHole));
        if (ownBottomlessTrapHole) {
            return true;
        }

        return false;
    }

    /** Whether we have no other face-up Spell or Trap with the same name. */
    protected boolean uniqueFaceupSpell() {
        return bot.getSpells().stream().noneMatch(c -> c.isCode(card.id) && c.isFaceup());
    }

    /** Whether we have no other face-up monster with the same name. */
    protected boolean uniqueFaceupMonster() {
        return bot.getMonsters().stream().noneMatch(c -> c.isCode(card.id) && c.isFaceup());
    }

    /** A simple way to keep the bot from chaining to itself in a mess. */
    protected boolean defaultDontChainMyself() {
        if (type != ExecutorType.ACTIVATE) {
            return true;
        }
        if (executors.stream().anyMatch(exec -> exec.type == type && card.isOriginalCode(exec.cardId))) {
            return false;
        }
        return duel.lastChainPlayer != 0;
    }

    /** Draw when we have lower LP, or destroy it. Can be overridden. */
    protected boolean defaultChickenGame() {
        if (executors.stream().filter(exec -> exec.type == type && card.isOriginalCode(exec.cardId)).count() > 1) {
            return false;
        }
        if (card.isFacedown()) {
            return true;
        }
        if (bot.lifePoints <= 1000) {
            return false;
        }
        if (bot.lifePoints <= enemy.lifePoints
                && activateDescription == util.getStringId(_CardId.ChickenGame, 0)) {
            return true;
        }
        if (bot.lifePoints > enemy.lifePoints
                && activateDescription == util.getStringId(_CardId.ChickenGame, 1)) {
            return true;
        }
        return false;
    }

    /** Draw when we have a DARK monster in hand to banish. Can be overridden. */
    protected boolean defaultAllureofDarkness() {
        ClientCard target = bot.hand.stream().filter(c -> c.hasAttribute(CardAttribute.DARK)).findFirst()
                .orElse(null);
        return target != null;
    }

    /** Clever enough. */
    protected boolean defaultDimensionalBarrier() {
        final int ritual = 0;
        final int fusion = 1;
        final int synchro = 2;
        final int xyz = 3;
        final int pendulum = 4;
        if (duel.player != 0) {
            List<ClientCard> monsters = enemy.getMonsters();
            Set<Integer> levels = new HashSet<>();
            boolean sameLevel = false;
            boolean tuner = false;
            boolean nontuner = false;
            for (ClientCard monster : monsters) {
                if (!monster.hasType(CardType.XYZ | CardType.LINK)) {
                    if (monster.hasType(CardType.TUNER)) {
                        tuner = true;
                    } else {
                        nontuner = true;
                    }
                    if (!monster.hasType(CardType.TOKEN) && monster.level > 0 && !levels.add(monster.level)) {
                        sameLevel = true;
                    }
                }

                if (monster.isOneForXyz()) {
                    ai.selectOption(xyz);
                    return true;
                }
            }
            if (tuner && nontuner) {
                ai.selectOption(synchro);
                return true;
            }
            if (sameLevel) {
                ai.selectOption(xyz);
                return true;
            }
            ClientCard l = enemy.spellZone[6];
            ClientCard r = enemy.spellZone[7];
            if (l != null && r != null && l.lScale != r.rScale) {
                ai.selectOption(pendulum);
                return true;
            }
        }
        ClientCard lastchaincard = util.getLastChainCard();
        if (duel.lastChainPlayer == 1 && lastchaincard != null && !lastchaincard.isDisabled()
                && (lastchaincard.hasType(CardType.SPELL | CardType.TRAP)
                        || lastchaincard.location == CardLocation.MONSTER_ZONE)) {
            if (lastchaincard.hasType(CardType.RITUAL)) {
                ai.selectOption(ritual);
                return true;
            }
            if (lastchaincard.hasType(CardType.FUSION)) {
                ai.selectOption(fusion);
                return true;
            }
            if (lastchaincard.hasType(CardType.SYNCHRO)) {
                ai.selectOption(synchro);
                return true;
            }
            if (lastchaincard.hasType(CardType.XYZ)) {
                ai.selectOption(xyz);
                return true;
            }
            if (lastchaincard.isFusionSpell()) {
                ai.selectOption(fusion);
                return true;
            }
        }
        if (util.isChainTarget(card)) {
            ai.selectOption(xyz);
            return true;
        }
        return false;
    }

    /** Clever enough. */
    protected boolean defaultInterruptedKaijuSlumber() {
        if (card.location == CardLocation.GRAVE) {
            ai.selectCard(
                    _CardId.GamecieltheSeaTurtleKaiju,
                    _CardId.KumongoustheStickyStringKaiju,
                    _CardId.GadarlatheMysteryDustKaiju,
                    _CardId.RadiantheMultidimensionalKaiju,
                    _CardId.DogorantheMadFlameKaiju,
                    _CardId.ThunderKingtheLightningstrikeKaiju,
                    _CardId.JizukirutheStarDestroyingKaiju);
            return true;
        }

        if (defaultDarkHole()) {
            ai.selectCard(
                    _CardId.JizukirutheStarDestroyingKaiju,
                    _CardId.ThunderKingtheLightningstrikeKaiju,
                    _CardId.DogorantheMadFlameKaiju,
                    _CardId.RadiantheMultidimensionalKaiju,
                    _CardId.GadarlatheMysteryDustKaiju,
                    _CardId.KumongoustheStickyStringKaiju,
                    _CardId.GamecieltheSeaTurtleKaiju);
            ai.selectNextCard(
                    _CardId.SuperAntiKaijuWarMachineMechaDogoran,
                    _CardId.GamecieltheSeaTurtleKaiju,
                    _CardId.KumongoustheStickyStringKaiju,
                    _CardId.GadarlatheMysteryDustKaiju,
                    _CardId.RadiantheMultidimensionalKaiju,
                    _CardId.DogorantheMadFlameKaiju,
                    _CardId.ThunderKingtheLightningstrikeKaiju);
            return true;
        }

        return false;
    }

    /** Clever enough. */
    protected boolean defaultKaijuSpsummon() {
        List<Integer> kaijus = List.of(
                _CardId.JizukirutheStarDestroyingKaiju,
                _CardId.GadarlatheMysteryDustKaiju,
                _CardId.GamecieltheSeaTurtleKaiju,
                _CardId.RadiantheMultidimensionalKaiju,
                _CardId.KumongoustheStickyStringKaiju,
                _CardId.ThunderKingtheLightningstrikeKaiju,
                _CardId.DogorantheMadFlameKaiju,
                _CardId.SuperAntiKaijuWarMachineMechaDogoran);
        for (ClientCard monster : enemy.getMonsters()) {
            if (monster.isCode(kaijus)) {
                return card.getDefensePower() > monster.getDefensePower();
            }
        }
        ClientCard target = CardContainer.getFloodgate(Arrays.asList(enemy.monsterZone));
        if (target != null) {
            ai.selectCard(target);
            return true;
        }
        target = CardContainer.getDangerousMonster(Arrays.asList(enemy.monsterZone));
        if (target != null) {
            ai.selectCard(target);
            return true;
        }
        target = util.getOneEnemyBetterThanValue(card.getDefensePower());
        if (target != null) {
            ai.selectCard(target);
            return true;
        }
        return false;
    }

    /** Summon when we don't have a monster with higher attack than the enemy's. */
    protected boolean defaultNumberS39UtopiaTheLightningSummon() {
        int bestBotAttack = util.getBestAttack(bot);
        return util.isOneEnemyBetterThanValue(bestBotAttack, false);
    }

    /**
     * Activate if this card is in Attack Position with ATK below 5000, when the battling enemy monster is in Attack
     * Position, face-down, or a face-up Defense Position monster worth attacking.
     */
    protected boolean defaultNumberS39UtopiaTheLightningEffect() {
        return card.isAttack() && card.attack < 5000 && (enemy.battlingMonster.isAttack()
                || enemy.battlingMonster.isFacedown() || enemy.battlingMonster.getDefensePower() >= card.attack);
    }

    /** Summon when it can and should use its effect. */
    protected boolean defaultEvilswarmExcitonKnightSummon() {
        int selfCount = bot.getMonsterCount() + bot.getSpellCount() + bot.getHandCount();
        int oppoCount = enemy.getMonsterCount() + enemy.getSpellCount() + enemy.getHandCount();
        return (selfCount - 1 < oppoCount) && defaultEvilswarmExcitonKnightEffect();
    }

    /** Activate when we have fewer cards than the enemy, or our ATK sum is lower than the enemy's. */
    protected boolean defaultEvilswarmExcitonKnightEffect() {
        int selfCount = bot.getMonsterCount() + bot.getSpellCount();
        int oppoCount = enemy.getMonsterCount() + enemy.getSpellCount();

        if (selfCount < oppoCount) {
            return true;
        }

        int selfAttack = bot.getMonsters().stream().mapToInt(ClientCard::getDefensePower).sum();
        int oppoAttack = enemy.getMonsters().stream().mapToInt(ClientCard::getDefensePower).sum();

        return selfAttack < oppoAttack;
    }

    /** Summon in Main Phase 2, or when our attack is lower than the enemy's but no enemy monster exceeds 2500. */
    protected boolean defaultStardustDragonSummon() {
        int selfBestAttack = util.getBestAttack(bot);
        int oppoBestAttack = util.getBestPower(enemy);
        return (selfBestAttack <= oppoBestAttack && oppoBestAttack <= 2500) || util.isTurn1OrMain2();
    }

    /** Negate the enemy's destruction effect, and revive from the GY. */
    protected boolean defaultStardustDragonEffect() {
        return (card.location == CardLocation.GRAVE) || duel.lastChainPlayer == 1;
    }

    /** Summon when the enemy has a card we must deal with. */
    protected boolean defaultCastelTheSkyblasterMusketeerSummon() {
        return util.getProblematicEnemyCard() != null;
    }

    /** Bounce the problematic enemy card. Ignore the first effect. */
    protected boolean defaultCastelTheSkyblasterMusketeerEffect() {
        if (activateDescription == util.getStringId(_CardId.CastelTheSkyblasterMusketeer, 0)) {
            return false;
        }
        ClientCard target = util.getProblematicEnemyCard();
        if (target != null) {
            ai.selectCard(0);
            ai.selectNextCard(target);
            return true;
        }
        return false;
    }

    /**
     * Summon when it should use its effect, or when our attack is lower than the enemy's but no enemy monster exceeds
     * 3000.
     */
    protected boolean defaultScarlightRedDragonArchfiendSummon() {
        int selfBestAttack = util.getBestAttack(bot);
        int oppoBestAttack = util.getBestPower(enemy);
        return (selfBestAttack <= oppoBestAttack && oppoBestAttack <= 3000)
                || defaultScarlightRedDragonArchfiendEffect();
    }

    protected boolean defaultTimelordSummon() {
        return bot.getMonsterCount() == 0;
    }

    /** Activate when we have fewer monsters than the enemy, or the enemy has 3 or more. */
    protected boolean defaultScarlightRedDragonArchfiendEffect() {
        int selfCount = (int) bot.getMonsters().stream().filter(monster -> monster != card
                && monster.isSpecialSummoned && monster.hasType(CardType.EFFECT)
                && monster.attack <= card.attack).count();
        int oppoCount = (int) enemy.getMonsters().stream().filter(monster -> monster.isSpecialSummoned
                && monster.hasType(CardType.EFFECT) && monster.attack <= card.attack).count();
        return selfCount <= oppoCount && oppoCount > 0 || oppoCount >= 3;
    }

    /** Clever enough. */
    protected boolean defaultHonestEffect() {
        if (card.location == CardLocation.HAND) {
            if (defaultCheckWhetherCardIsNegated(card)) {
                return false;
            }
            return bot.battlingMonster.isAttack()
                    && ((bot.battlingMonster.attack < enemy.battlingMonster.attack)
                            || bot.battlingMonster.attack >= enemy.lifePoints
                            || ((bot.battlingMonster.attack < enemy.battlingMonster.defense)
                                    && (bot.battlingMonster.attack + enemy.battlingMonster.attack
                                            > enemy.battlingMonster.defense)));
        }

        return util.isTurn1OrMain2();
    }

    /** Always activate. */
    protected boolean defaultVaylantzWorld_ShinraBansho() {
        if (defaultSpellWillBeNegated()) {
            return false;
        }

        return true;
    }

    /** Select the enemy's best monster. */
    protected boolean defaultVaylantzWorld_KonigWissen() {
        if (defaultSpellWillBeNegated()) {
            return false;
        }

        List<ClientCard> monsters = enemy.getMonsters();
        if (monsters.isEmpty()) {
            return false;
        }

        Comparator<ClientCard> byAttackDescending = Comparator.comparingInt((ClientCard c) -> c.attack).reversed();
        List<ClientCard> targetList = new ArrayList<>();
        List<ClientCard> floodgateCards = monsters.stream()
                .filter(c -> c != null && c.data != null && c.isFloodgate() && c.isFaceup()
                        && !c.isShouldNotBeTarget())
                .sorted(byAttackDescending).toList();
        List<ClientCard> dangerousCards = monsters.stream()
                .filter(c -> c != null && c.data != null && c.isMonsterDangerous() && c.isFaceup()
                        && !c.isShouldNotBeTarget())
                .sorted(byAttackDescending).toList();
        List<ClientCard> attackOrderedCards = monsters.stream()
                .filter(c -> c != null && c.data != null && c.hasType(CardType.MONSTER) && c.isFaceup()
                        && !c.isShouldNotBeTarget())
                .sorted(byAttackDescending).toList();

        targetList.addAll(floodgateCards);
        targetList.addAll(dangerousCards);
        targetList.addAll(attackOrderedCards);

        if (!targetList.isEmpty()) {
            ai.selectCard(targetList);
            return true;
        }

        return false;
    }

    /** {@link #defaultCheckWhetherBotWillBeBanished(ClientCard)} with an unknown card. */
    protected boolean defaultCheckWhetherBotWillBeBanished() {
        return defaultCheckWhetherBotWillBeBanished((ClientCard) null);
    }

    /**
     * Whether the given bot card will be banished if it is sent to the GY.
     *
     * @param card the bot card that would be sent to the GY; {@code null} uses an unknown type and location
     * @return true if the card would be banished instead of going to the GY
     */
    protected boolean defaultCheckWhetherBotWillBeBanished(ClientCard card) {
        if (card == null) {
            return defaultCheckWhetherBotWillBeBanished(0, 0);
        }
        return defaultCheckWhetherBotWillBeBanished(card.type, card.location);
    }

    /** {@link #defaultCheckWhetherBotWillBeBanished(int, int)} with an unknown location. */
    protected boolean defaultCheckWhetherBotWillBeBanished(int cardType) {
        return defaultCheckWhetherBotWillBeBanished(cardType, 0);
    }

    /**
     * Whether a bot card of the given type and location will be banished if it is sent to the GY. Type 0 skips
     * type-filtered redirects. Location 0 is not Overlay, but also not Hand, Deck, or Monster Zone.
     *
     * @param cardType type flags of the card that would be sent to the GY; 0 means unknown
     * @param location current location of the card that would be sent to the GY; 0 means unknown
     * @return true if the card would be banished instead of going to the GY
     */
    protected boolean defaultCheckWhetherBotWillBeBanished(int cardType, int location) {
        // Effects that prevent banishing stop GY redirects: Artifact Lancea until the end of the turn it resolves,
        // Imperial Iron Wall while face-up and not disabled in either Spell/Trap Zone.
        if (resolvedEffectIdList.contains(_CardId.ArtifactLancea)
                || bot.hasInSpellZone(_CardId.ImperialIronWall, true, true)
                || enemy.hasInSpellZone(_CardId.ImperialIronWall, true, true)) {
            return false;
        }

        if (dimensionShifterCount > 0) {
            return true;
        }

        List<ClientField> fields = List.of(bot, enemy);

        // Face-up and not disabled: Banisher of the Light/Radiance and Kashtira Arise-Heart (no material required)
        // only in the Monster Zone; Macro Cosmos only in the Spell/Trap Zone.
        List<Integer> monsterRedirectIds = List.of(
                _CardId.BanisheroftheRadiance,
                _CardId.BanisheroftheLight,
                _CardId.KashtiraAriseHeart);
        for (ClientField field : fields) {
            if (field.hasInMonstersZone(monsterRedirectIds, true, false, true)
                    || field.hasInSpellZone(_CardId.MacroCosmos, true, true)) {
                return true;
            }
        }

        // Retaliating "C" only redirects while Special Summoned by its own effect and still face-up there.
        if (retaliatingCSummonedByOwnEffect.stream().anyMatch(c ->
                c != null && c.isCode(_CardId.RetaliatingC)
                        && c.location == CardLocation.MONSTER_ZONE && c.isFaceup() && !c.isDisabled())) {
            return true;
        }

        // Masked HERO Dark Law and F.A. Hang On Mach only banish cards owned by the opponent of their controller.
        if (enemy.hasInMonstersZone(_CardId.MaskedHERODarkLaw, true, false, true)) {
            return true;
        }
        for (ClientCard monster : enemy.getMonsters()) {
            if (monster != null && monster.isCode(_CardId.FAHangOnMach)
                    && monster.isFaceup() && !monster.isDisabled() && monster.level >= 7) {
                return true;
            }
        }

        // Amorphage Goliath in a Pendulum Zone redirects non-Amorphage cards while that player controls an Amorphage
        // monster. The setcode isn't known from (type, location), so treat the field condition as "will be banished".
        for (ClientField field : fields) {
            if (field.hasInPendulumZone(_CardId.AmorphageGoliath, true)
                    && field.getMonsters().stream().anyMatch(monster -> monster.isFaceup()
                            && monster.hasSetcode(_Setcode.Amorphage))) {
                return true;
            }
        }

        if (defaultIsDimensionalFissureTarget(cardType, location)) {
            // Different Dimension Ground is recorded on resolve and cleared with resolvedEffectIdList each turn.
            if (resolvedEffectIdList.contains(_CardId.DifferentDimensionGround)) {
                return true;
            }
            for (ClientField field : fields) {
                if (field.hasInSpellZone(_CardId.DimensionalFissure, true, true)) {
                    return true;
                }
            }
            // The script counts face-up Uria/Hamon/Raviel on the controller's field (Monster and Spell/Trap Zones).
            List<Integer> sacredBeastIds = List.of(
                    _CardId.UriaLordofSearingFlames,
                    _CardId.HamonLordofStrikingThunder,
                    _CardId.RavielLordofPhantasms);
            if (enemy.hasInSpellZone(_CardId.AwakeningoftheSacredBeasts, true, true)
                    && sacredBeastIds.stream().allMatch(id -> enemy.hasInMonstersZone(id, false, false, true)
                            || enemy.hasInSpellZone(id, false, true))) {
                return true;
            }
        }

        // Antihuman Intelligence ME-PSY-YA in a Pendulum Zone redirects non-monster original types.
        // Skip when the type is unknown so a generic monster check does not treat it as a non-monster dump.
        if (cardType != 0 && (cardType & CardType.MONSTER) == 0) {
            for (ClientField field : fields) {
                if (field.hasInPendulumZone(_CardId.AntihumanIntelligenceMEPSYYA, true)) {
                    return true;
                }
            }
        }

        // Herald of the Arc Light has no IGNORE_RANGE, so it only redirects monsters from the Hand or Deck.
        if ((cardType & CardType.MONSTER) != 0
                && (location & (CardLocation.HAND | CardLocation.DECK)) != 0) {
            for (ClientField field : fields) {
                if (field.hasInMonstersZone(_CardId.HeraldoftheArcLight, true, false, true)) {
                    return true;
                }
            }
        }

        // Ryu-Ge Realm - Wyrm Winds redirects monsters leaving the Monster Zone on its controller's opponent's turn.
        if ((cardType & CardType.MONSTER) != 0
                && (location & CardLocation.MONSTER_ZONE) != 0) {
            if (duel.player == 1 && bot.hasInSpellZone(_CardId.RyuGeRealmWyrmWinds, true, true)) {
                return true;
            }
            if (duel.player == 0 && enemy.hasInSpellZone(_CardId.RyuGeRealmWyrmWinds, true, true)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Matches aux.DimensionalFissureTarget: original monster type, not Overlay, and not a Spell/Trap type. An unknown
     * type does not match; an unknown location is treated as not Overlay.
     */
    private boolean defaultIsDimensionalFissureTarget(int cardType, int location) {
        if (cardType == 0) {
            return false;
        }
        if ((cardType & CardType.MONSTER) == 0) {
            return false;
        }
        if ((cardType & (CardType.SPELL | CardType.TRAP)) != 0) {
            return false;
        }
        if ((location & CardLocation.OVERLAY) != 0) {
            return false;
        }
        return true;
    }

    protected boolean defaultCheckWhetherCardIsNegated(ClientCard card) {
        if (card == null) {
            return true;
        }
        if (card.data == null) {
            return card.isDisabled();
        }
        int originId = card.data.data().alias();
        if (originId == 0) {
            originId = card.data.code();
        }
        return crossoutDesignatorIdList.contains(originId)
                || (calledbytheGraveIdCountMap.containsKey(originId) && calledbytheGraveIdCountMap.get(originId) > 0)
                || (card.isDisabled() && (card.location & CardLocation.ONFIELD) > 0);
    }

    protected boolean defaultCheckWhetherCardIdIsNegated(int cardId) {
        return crossoutDesignatorIdList.contains(cardId)
                || (calledbytheGraveIdCountMap.containsKey(cardId) && calledbytheGraveIdCountMap.get(cardId) > 0);
    }

    protected int getCalledbytheGraveIdCount(int cardId) {
        return calledbytheGraveIdCountMap.getOrDefault(cardId, 0);
    }

    protected boolean defaultCheckWhetherNumber41IsActive() {
        return Stream.concat(Arrays.stream(bot.monsterZone), Arrays.stream(enemy.monsterZone)).anyMatch(c ->
                c != null && c.isFaceup() && c.isCode(_CardId.Number41BagooskatheTerriblyTiredTapir)
                        && c.isDefense() && !c.isDisabled());
    }

    protected boolean defaultSetForDiabellze() {
        if (card == null) {
            return false;
        }
        if (card.id == _CardId.PotOfExtravagance) {
            return false;
        }
        if (enemy.hasInMonstersZone(_CardId.DiabellzeOfTheOriginalSin, true, false, true)
                && card.hasType(CardType.SPELL) && !card.hasType(CardType.QUICK_PLAY)) {
            // As in WindBot, the Quick-Play check looks at the card being set, not the one in the zone.
            if (Arrays.stream(bot.spellZone).anyMatch(c -> c != null && duel.mainPhase.activableCards.contains(c)
                    && c.hasType(CardType.SPELL) && !card.hasType(CardType.QUICK_PLAY) && c.isFacedown())) {
                return false;
            }
            for (CardExecutor exec : executors) {
                if (exec.type == ExecutorType.ACTIVATE && card.isOriginalCode(exec.cardId)) {
                    if (exec.func == null || exec.func.getAsBoolean()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Whether every available Spell/Trap column is negated. */
    protected boolean defaultCheckAllAvailableSpellColumnNegated() {
        for (int i = 0; i < 5; i++) {
            // occupied
            if (bot.spellZone[i] != null) {
                continue;
            }
            // negated
            if (infiniteImpermanenceNegatedColumns.contains(i)) {
                continue;
            }
            // an empty column that isn't negated
            return false;
        }
        // all columns are negated
        return true;
    }

    /** Whether activating the spell will be negated. */
    protected boolean defaultCheckWhetherSpellActivateWillBeNegated(ClientCard card) {
        if (card == null) {
            return false;
        }
        if (card.location == CardLocation.SPELL_ZONE) {
            return infiniteImpermanenceNegatedColumns.contains(card.sequence);
        }
        // whether it will be negated by Infinite Impermanence
        return defaultCheckAllAvailableSpellColumnNegated();
    }

    /** Whether the bot can search cards from the Deck. */
    protected boolean defaultCheckWhetherBotCanSearch() {
        if (resolvedEffectIdList.contains(_CardId.LockBird)) {
            return false;
        }
        if (mistakenArrestAffectedCount > 0) {
            return false;
        }
        if (bot.hasInMonstersZone(_CardId.ThunderKingRaiOh, true, false, true)
                || enemy.hasInMonstersZone(_CardId.ThunderKingRaiOh, true, false, true)) {
            return false;
        }
        if (enemy.hasInMonstersZone(_CardId.ThunderDragonColossus, true, false, true)) {
            return false;
        }
        if (bot.hasInSpellZone(_CardId.DeckLockdown, true, true)
                || enemy.hasInSpellZone(_CardId.DeckLockdown, true, true)
                || bot.hasInSpellZone(_CardId.Mistake, true, true)
                || enemy.hasInSpellZone(_CardId.Mistake, true, true)) {
            return false;
        }
        if (enemy.hasInSpellZone(_CardId.DoomZDestruction, true, true)) {
            return false;
        }
        return true;
    }

    /** Whether the bot can draw cards. */
    protected boolean defaultCheckWhetherBotCanDraw() {
        if (resolvedEffectIdList.contains(_CardId.LockBird)) {
            return false;
        }
        return true;
    }

    /** Whether the enemy can draw cards. */
    protected boolean defaultCheckWhetherEnemyCanDraw() {
        if (resolvedEffectIdList.contains(_CardId.LockBird)) {
            return false;
        }
        return true;
    }
}
