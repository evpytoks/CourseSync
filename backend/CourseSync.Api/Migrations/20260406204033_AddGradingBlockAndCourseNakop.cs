using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace CourseSync.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddGradingBlockAndCourseNakop : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<decimal>(
                name: "block",
                table: "course_grading_elements",
                type: "numeric(6,2)",
                nullable: false,
                defaultValue: 0m);

            migrationBuilder.CreateTable(
                name: "course_nakop",
                columns: table => new
                {
                    course_id = table.Column<Guid>(type: "uuid", nullable: false),
                    block = table.Column<decimal>(type: "numeric(6,2)", nullable: true),
                    automatic_threshold = table.Column<decimal>(type: "numeric(6,2)", nullable: true),
                    updated_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_course_nakop", x => x.course_id);
                    table.ForeignKey(
                        name: "FK_course_nakop_courses_course_id",
                        column: x => x.course_id,
                        principalTable: "courses",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateTable(
                name: "course_nakop_elements",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    course_id = table.Column<Guid>(type: "uuid", nullable: false),
                    element_name = table.Column<string>(type: "character varying(50)", maxLength: 50, nullable: false),
                    position = table.Column<int>(type: "integer", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_course_nakop_elements", x => x.id);
                    table.ForeignKey(
                        name: "FK_course_nakop_elements_course_nakop_course_id",
                        column: x => x.course_id,
                        principalTable: "course_nakop",
                        principalColumn: "course_id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_course_nakop_elements_course_id",
                table: "course_nakop_elements",
                column: "course_id");

            migrationBuilder.CreateIndex(
                name: "IX_course_nakop_elements_course_id_position",
                table: "course_nakop_elements",
                columns: new[] { "course_id", "position" });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "course_nakop_elements");

            migrationBuilder.DropTable(
                name: "course_nakop");

            migrationBuilder.DropColumn(
                name: "block",
                table: "course_grading_elements");
        }
    }
}
